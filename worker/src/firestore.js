import {ApiError} from "./validation.js";
import {googleAccessToken, httpFetch} from "./google.js";

export function encode(value) {
  if (value === null) return {nullValue: null};
  if (value instanceof Date) return {timestampValue: value.toISOString()};
  if (typeof value === "string") return {stringValue: value};
  if (typeof value === "boolean") return {booleanValue: value};
  if (typeof value === "number") return Number.isInteger(value) ? {integerValue: String(value)} : {doubleValue: value};
  if (Array.isArray(value)) return {arrayValue: {values: value.map(encode)}};
  return {mapValue: {fields: fields(value)}};
}
export function fields(value) { return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, encode(item)])); }
export function decode(value) {
  if ("nullValue" in value) return null;
  if ("stringValue" in value) return value.stringValue;
  if ("booleanValue" in value) return value.booleanValue;
  if ("integerValue" in value) return Number(value.integerValue);
  if ("doubleValue" in value) return value.doubleValue;
  if ("timestampValue" in value) return new Date(value.timestampValue);
  if ("arrayValue" in value) return (value.arrayValue.values || []).map(decode);
  return Object.fromEntries(Object.entries(value.mapValue?.fields || {}).map(([key, item]) => [key, decode(item)]));
}
function document(raw) { return raw ? {path: raw.name.split("/documents/")[1], data: decode({mapValue: {fields: raw.fields}}), updateTime: raw.updateTime} : null; }
export class Firestore {
  constructor(env, emulator) {
    this.env = env;
    this.root = `projects/${env.FIREBASE_PROJECT_ID}/databases/(default)/documents`;
    // Emulator URL is supplied only by the local integration tests, never by an HTTP request or production config.
    this.endpoint = emulator || "https://firestore.googleapis.com/v1";
    this.emulator = !!emulator;
  }
  async request(suffix, body, method = "POST") {
    const token = this.emulator ? "owner" : await googleAccessToken(this.env);
    const response = await httpFetch(this.endpoint + "/" + this.root + suffix, {
      method, headers: {Authorization: "Bearer " + token, "Content-Type": "application/json"},
      ...(body === undefined ? {} : {body: JSON.stringify(body)})});
    const data = await response.json();
    const failure = Array.isArray(data) ? data.find(item => item.error)?.error : data.error;
    if (!response.ok || failure) {
      if (response.status === 404) return null;
      const status = failure?.status || "UNAVAILABLE";
      console.warn("firestore_request_failed", status, failure?.message || "");
      const error = new ApiError(response.status === 409 ? 409 : 503,
        response.status === 409 ? "transaction-conflict" : "database-" + status.toLowerCase().replaceAll("_", "-"));
      error.retryable = ["ABORTED", "FAILED_PRECONDITION"].includes(status); throw error;
    }
    return data;
  }
  async get(path, transaction) {
    const docs = await this.request(":batchGet", {documents: [this.root + "/" + path], ...(transaction ? {transaction} : {})});
    return document(docs.find(item => item.found)?.found);
  }
  async getAll(paths, transaction) {
    if (!paths.length) return [];
    const docs = await this.request(":batchGet", {documents: paths.map(p => this.root + "/" + p), ...(transaction ? {transaction} : {})});
    const mapped = new Map(docs.filter(item => item.found).map(item => { const doc = document(item.found); return [doc.path, doc]; }));
    return paths.map(p => mapped.get(p) || null);
  }
  write(path, data, exists) {
    return {update: {name: this.root + "/" + path, fields: fields(data)},
      ...(exists === undefined ? {} : {currentDocument: {exists}})};
  }
  remove(path) { return {delete: this.root + "/" + path}; }
  async commit(writes, transaction) { return this.request(":commit", {writes, ...(transaction ? {transaction} : {})}); }
  async transaction(action) {
    let retryTransaction;
    for (let attempt = 0; attempt < 5; attempt++) {
      const started = await this.request(":beginTransaction", {options: {readWrite: retryTransaction ? {retryTransaction} : {}}});
      try {
        const pending = [];
        const result = await action({get: path => this.get(path, started.transaction),
          getAll: paths => this.getAll(paths, started.transaction),
          set: (path, value, exists) => pending.push(this.write(path, value, exists)),
          delete: path => pending.push(this.remove(path))});
        if (pending.length) await this.commit(pending, started.transaction);
        else await this.request(":rollback", {transaction: started.transaction});
        return result;
      } catch (error) {
        await this.request(":rollback", {transaction: started.transaction}).catch(() => {});
        if (!error.retryable || attempt === 4) throw error;
        retryTransaction = started.transaction;
        // Separate simultaneous contenders instead of restarting in lockstep.
        await new Promise(resolve => setTimeout(resolve, Math.min(400, 50 * 2 ** attempt) + Math.floor(Math.random() * 150)));
      }
    }
  }
  async query(collection, filters = [], orderBy, limit = 20) {
    const result = await this.request(":runQuery", {structuredQuery: {
      from: [{collectionId: collection}],
      ...(filters.length ? {where: {compositeFilter: {op: "AND", filters: filters.map(([field, op, value]) => ({fieldFilter: {field: {fieldPath: field}, op, value: encode(value)}}))}}} : {}),
      ...(orderBy ? {orderBy: [{field: {fieldPath: orderBy}, direction: "ASCENDING"}]} : {}), limit}});
    return result.filter(item => item.document).map(item => document(item.document));
  }
}
