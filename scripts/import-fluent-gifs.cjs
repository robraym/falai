// Rebuild with: npm install --prefix build/gif-tools --no-package-lock sharp upng-js gifsicle
// node scripts/import-fluent-gifs.cjs
// MIT-licensed Microsoft Fluent Emoji Animated, pinned to a reviewed commit.
const fs = require('fs'), path = require('path');
const U = require('../build/gif-tools/node_modules/upng-js');
const sharp = require('../build/gif-tools/node_modules/sharp');
const REF = 'daa0365c09795789ed2bc6e8b228c97736cb6669';
const categories = ['Reações', 'Carinho', 'Gestos', 'Comemorações', 'Animais', 'Dia a dia', 'Natureza'];
const offers = [
// Keep the six original message IDs, replacing only their artwork.
['heart','Red heart','Coração',1],['laugh','Face with tears of joy','Risada',0],['party','Partying face','Festa',3],
['sleep','Sleeping face','Sono',0],['coffee','Hot beverage','Café',5],['applause','Clapping hands','Palmas',2],
['smile','Grinning face','Sorriso',0],['happy','Beaming face with smiling eyes','Muito feliz',0],
['rofl','Rolling on the floor laughing','Morrendo de rir',0],['wink','Winking face','Piscadinha',0],
['cool','Smiling face with sunglasses','De boa',0],['thinking','Thinking face','Pensando',0],
['surprised','Astonished face','Surpresa',0],['sad','Crying face','Triste',0],['crying','Loudly crying face','Chorando',0],
['angry','Angry face','Bravo',0],['shocked','Exploding head','Chocado',0],['melting','Melting face','Derretendo',0],
['eyeroll','Face with rolling eyes','Revirando os olhos',0],['shy','Flushed face','Sem graça',0],
['silly','Zany face','Brincadeira',0],['tired','Yawning face','Cansaço',0],['pleading','Pleading face','Por favor',0],
['shush','Shushing face','Segredinho',0],
['kiss','Face blowing a kiss','Beijinho',1],['loving','Smiling face with hearts','Apaixonado',1],
['heart_eyes','Smiling face with heart-eyes','Olhos de coração',1],['hug','Hugging face','Abraço',1],
['beating_heart','Beating heart','Coração batendo',1],['sparkling_heart','Sparkling heart','Coração brilhante',1],
['two_hearts','Two hearts','Dois corações',1],['love_letter','Love letter','Carta de amor',1],
['broken_heart','Broken heart','Coração partido',1],['mending_heart','Mending heart','Coração se curando',1],
['heart_hands','Heart hands','Coração com as mãos',1],
['thumbs_up','Thumbs up','Legal',2],['thumbs_down','Thumbs down','Não gostei',2],['wave','Waving hand','Olá',2],
['thanks','Folded hands','Obrigado',2],['victory','Victory hand','Vitória',2],['strong','Flexed biceps','Força',2],
['fingers_crossed','Crossed fingers','Na torcida',2],['handshake','Handshake','Combinado',2],['love_you','Love-you gesture','Te amo',2],
['confetti','Party popper','Confete',3],['cake','Birthday cake','Aniversário',3],['cheers','Clinking glasses','Brinde',3],
['champagne','Bottle with popping cork','Comemorar',3],['dancing','Woman dancing','Dançando',3],
['star_struck','Star-struck','Maravilhado',3],['hundred','Hundred points','Nota cem',3],
['cat','Cat face','Gatinho',4],['dog','Dog face','Cachorrinho',4],['panda','Panda','Panda',4],
['bear','Bear','Ursinho',4],['rabbit','Rabbit face','Coelhinho',4],['frog','Frog','Sapinho',4],
['penguin','Penguin','Pinguim',4],['chick','Hatching chick','Pintinho',4],['fox','Fox','Raposa',4],['unicorn','Unicorn','Unicórnio',4],
['tea','Teacup without handle','Chá',5],['alarm','Alarm clock','Despertador',5],['rocket','Rocket','Foguete',5],
['eyes','Eyes','De olho',5],['music','Musical notes','Música',5],
['sun','Sun with face','Sol',6],['moon','Full moon face','Lua',6],['rainbow','Rainbow','Arco-íris',6],
['rain','Cloud with rain','Chuva',6],['fire','Fire','Fogo',6],['butterfly','Butterfly','Borboleta',6]
];
const root = path.join(__dirname,'..'), cache = path.join(root,'build/fluent-gifs'); fs.mkdirSync(cache,{recursive:true});
async function download(url, target) {
  if (fs.existsSync(target)) return fs.readFileSync(target);
  const response = await fetch(url); if (!response.ok) throw Error('Asset download HTTP '+response.status);
  const bytes = Buffer.from(await response.arrayBuffer()); fs.writeFileSync(target, bytes); return bytes;
}
async function convert(bytes) {
  const image = U.decode(bytes.buffer.slice(bytes.byteOffset,bytes.byteOffset+bytes.byteLength));
  if(image.width!==256 || image.height!==256 || image.frames.length<2) throw Error('Expected animated 256px PNG');
  const frames = U.toRGBA8(image), delays = image.frames.map(frame=>frame.delay);
  const bytesOut = await sharp(Buffer.concat(frames.map(frame=>Buffer.from(frame))),
    {raw:{width:256,height:256*frames.length,channels:4,pageHeight:256}})
    .gif({colours:256,dither:1,effort:7,loop:0,delay:delays}).toBuffer();
  return {bytes:bytesOut,frames:frames.length,duration:delays.reduce((sum,delay)=>sum+delay,0)};
}
async function main() {
  const treeFile=path.join(root,'build/fluent-tree.json');
  const treeBytes=await download('https://api.github.com/repos/microsoft/fluentui-emoji-animated/git/trees/'+REF+'?recursive=1',treeFile);
  const tree = JSON.parse(treeBytes).tree;
  const manifest=[];
  for(const [id,name,label,category] of offers){
    const candidates=tree.filter(entry=>entry.path.startsWith('assets/'+name+'/')&&entry.path.endsWith('.png'));
    const source=candidates.find(entry=>entry.path.includes('/Default/'))||candidates.find(entry=>entry.path.split('/').length===4);
    if(!source)throw Error('Missing source '+name);
    const url='https://media.githubusercontent.com/media/microsoft/fluentui-emoji-animated/'+REF+'/'+source.path.split('/').map(encodeURIComponent).join('/');
    const input=await download(url,path.join(cache,id+'.png'));
    const result=await convert(input), target=path.join(root,'app/src/main/res/raw/gif_'+id+'.gif');fs.writeFileSync(target,result.bytes);
    const {default:optimizer}=await import('../build/gif-tools/node_modules/gifsicle/index.js');
    const optimized=path.join(cache,id+'.optimized.gif');
    const process=require('child_process').spawnSync(optimizer,['-O3','--careful',target,'-o',optimized]);
    if(process.status!==0)throw Error('GIF optimization failed for '+id);
    result.bytes=fs.readFileSync(optimized);fs.writeFileSync(target,result.bytes);
    const actual=await sharp(result.bytes,{animated:true}).metadata();
    result.frames=actual.pages;result.duration=actual.delay.reduce((sum,delay)=>sum+delay,0);
    manifest.push({id,label,category,source:source.path,sha256:require('crypto').createHash('sha256').update(input).digest('hex'),width:256,height:256,frames:result.frames,duration:result.duration,bytes:result.bytes.length});
    console.log(`${manifest.length}/${offers.length} ${id}: ${result.bytes.length} bytes, ${result.frames} frames`);
  }
  const license=await download('https://raw.githubusercontent.com/microsoft/fluentui-emoji-animated/'+REF+'/LICENSE',path.join(cache,'LICENSE'));
  fs.writeFileSync(path.join(root,'app/src/main/assets/licenses/FLUENT-EMOJI-ANIMATED.txt'),license);
  fs.writeFileSync(path.join(root,'app/src/main/assets/licenses/fluent-gifs.json'),JSON.stringify({repository:'https://github.com/microsoft/fluentui-emoji-animated',commit:REF,assets:manifest},null,2)+'\n');
  fs.writeFileSync(path.join(root,'app/src/main/res/values/gifs.xml'),'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'+
    offers.map(([id,,label])=>`    <string name="gif_${id}">${label}</string>`).join('\n')+'\n    <string-array name="gif_categories">\n'+categories.map(label=>`        <item>${label}</item>`).join('\n')+'\n    </string-array>\n</resources>\n');
  const catalog=path.join(root,'app/src/main/java/br/com/falai/GifCatalog.java');let code=fs.readFileSync(catalog,'utf8');
  code=code.replace(/static final String\[\] IDS = \{[^;]+;/,'static final String[] IDS = {'+offers.map(([id])=>JSON.stringify(id)).join(', ')+'};');
  code=code.replace(/static final int\[\] FILES = \{[^;]+;/,'static final int[] FILES = {'+offers.map(([id])=>'R.raw.gif_'+id).join(', ')+'};');
  code=code.replace(/static final int\[\] LABELS = \{[^;]+;/,'static final int[] LABELS = {'+offers.map(([id])=>'R.string.gif_'+id).join(', ')+'};');
  code=code.replace(/    static final int\[\] GROUPS = [^;]+;\r?\n/,'');
  code=code.replace('    static String message(', '    static final int[] GROUPS = {'+offers.map(item=>item[3]).join(', ')+'};\n    static String message(');
  code=code.replace('Original bundled GIFs.','Bundled Microsoft Fluent GIFs (MIT); see assets/licenses/FLUENT-EMOJI-ANIMATED.txt.');fs.writeFileSync(catalog,code);
  console.log('Total GIFs:',offers.length,'bytes:',manifest.reduce((sum,item)=>sum+item.bytes,0));
}
main().catch(error=>{console.error(error.message);process.exitCode=1;});
