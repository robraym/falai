# Falai

Aplicativo Android nativo em **Java**, com texto e emojis, foto de perfil, login Google, descoberta de contatos por e-mail e o visual do Alertou.

O seletor ampliado organiza 120 emojis em seis categorias e mantém até 24 recentes em uma fileira horizontal no topo, salvos por conta no aparelho. Os balões enviados e recebidos usam um contorno contínuo com bicos espelhados.

A lupa dentro da conversa começa no canto inferior direito e pode ser arrastada, com posição salva. Ela filtra as mensagens já carregadas, ignorando diferenças de acentos e maiúsculas. O botão de mensagens anteriores permite ampliar o histórico pesquisado sem baixar toda a conversa automaticamente.

O cabeçalho da conversa mostra “digitando…” e presença no Falai. A digitação é renovada no máximo a cada quatro segundos, encerrada após 2,5 segundos sem alterações e expira após oito segundos se a conexão cair. A presença usa sessões por instalação, atualizadas a cada 30 segundos enquanto o app está em primeiro plano, com tela interativa e desbloqueada. Sem encerramento normal, o estado online expira em até 70 segundos após a última atualização. “Visto há…” representa a última atividade informada pelo app, não um monitoramento geral do aparelho. Esses estados consomem leituras/escritas da cota do Firestore.

A tela de dados da pessoa, aberta ao tocar na foto ou no nome no cabeçalho da conversa, mostra a data de geração embutida no APK da instalação registrada mais recentemente. A lista de Contatos não mostra essa informação. Versões antigas exibem “Data do APK não informada”. A data é informada pelo cliente, não é uma atestação de integridade e não representa a data de instalação.

A aparência acompanha automaticamente o tema claro/escuro do aparelho, com as paletas correspondentes do Alertou. O cabeçalho respeita a barra de status, inclusive ao trocar de aba, e a lista de conversas mostra a data e a hora da última mensagem.

O histórico de cada conversa usa papel de parede com desenhos vetoriais discretos, fundo bege no modo claro e azul-escuro no modo escuro. Na lista, a prévia e a data/hora ficam na mesma linha; o espaço abaixo do título foi reduzido em 8dp.

## Arquitetura gratuita

- **Firebase Authentication / Google**: identifica a conta sem SMS.
- **Firestore Standard / Spark**: guarda perfis e conversas e fornece as atualizações em tempo real.
- **Firebase Cloud Messaging (FCM)**: entrega notificações com o app em segundo plano ou fechado normalmente.
- **Cloudflare Workers Free**: valida o token Firebase, executa as operações do servidor e dispara o FCM. Substitui completamente as Cloud Functions; não exige Blaze.

Não há Firebase Storage, envio de mídia nas conversas, SMS nem código implantável de Cloud Functions neste projeto. Fotos de perfil são miniaturas no Firestore. O Firebase permanece no Spark e o Worker no Free. As cotas desses planos continuam se aplicando; esgotá-las pode interromper o serviço. Não existe garantia de disponibilidade ou uso ilimitado.

## Configuração usada

- Pacote: `br.com.falai`.
- Projeto Firebase: `falai-4d04c`.
- Firestore: `(default)`, Standard, `southamerica-east1`.
- Worker: `https://falai-notificacoes.robraym-studio.workers.dev`.
- JSON do Android: `app/google-services.json`, ignorado no Git.
- Variável pública do Worker: `FIREBASE_PROJECT_ID`.
- Segredo do Worker: `FIREBASE_SERVICE_ACCOUNT`, com o JSON da conta de serviço `falai-worker`. A chave privada nunca entra no APK, no repositório ou na resposta da API.
- Papéis da conta de serviço: `roles/firebasecloudmessaging.admin` e `roles/datastore.user`.

Google Authentication, SHA-1 do debug e o JSON com o cliente OAuth Web foram configurados pelo responsável pelo app. A assinatura release ainda deve ser definida antes da distribuição e seu certificado precisa ser cadastrado no Firebase.

## Fluxo do aplicativo

Quem instala apenas entra com Google, confirma o nome e escolhe se autoriza agenda e notificações. Não há configuração técnica para o usuário final.

Em Conversas, o botão flutuante verde abre a tela de seleção de contato; escolher alguém inicia ou reabre a conversa e volta diretamente à lista ao sair do chat. Não há botão textual “Encontrar contatos”. O cabeçalho e o contorno integrado às três abas seguem as medidas do Alertou. Emoji, campo de texto e envio ficam na mesma linha, com o campo crescendo até cinco linhas.

**A agenda é cruzada pelos e-mails, não pelos números de telefone.** O e-mail salvo no contato precisa corresponder à conta Google usada no Falai. Contatos com apenas telefone precisam ter esse e-mail adicionado na agenda. Apenas e-mails são enviados na descoberta; nomes da agenda permanecem no aparelho. Não há botão de busca manual por e-mail. A permissão de leitura da agenda é necessária para a descoberta automática.

Os resultados ficam em memória por dez minutos; ao abrir a aba após esse prazo, a agenda é consultada novamente. Alterações na agenda invalidam esse cache automaticamente. Não há botão de atualização. Há um limite de 3.000 e-mails por consulta e lotes de 100. A contagem no cabeçalho é de pessoas encontradas no Falai, não o total bruto de contatos do aparelho.

O ícone no cabeçalho abre **Perfil**, com foto, nome editável e e-mail Google somente para leitura. Em **Ajustes**, a seção Aparência oferece seis cores e tamanho dos textos de 70% a 150%, além das linhas compactas de notificações, privacidade e saída, sem cards por seção. O tema claro/escuro continua seguindo o aparelho. A pesquisa flutuante reutiliza o controlador do Alertou: arraste, posição relativa salva, expansão, teclado, recolhimento ao tocar fora/voltar e filtro sem diferença de acentos.

O Perfil também oferece **Recado**, uma frase editável de até 139 unidades UTF-16, com contador. Salvar vazio remove o recado. Ele aparece em tempo real abaixo do nome na lista de contatos e no painel aberto ao tocar no nome/foto do chat. Apenas a frase e sua data de alteração ficam na coleção compartilhada `profileAbout`; o e-mail e os demais dados privados não são expostos. Usuários autenticados podem ler um recado individual, mas não listar a coleção nem gravá-la diretamente. Há limite de 20 alterações por usuário/hora.

As fotos são recortadas ao centro, orientadas, reduzidas a 192 × 192 e comprimidas em JPEG de até 16 KiB. O servidor valida tamanho e dimensões (máximo 256 × 256), salva somente na conta autenticada e não permite listagem da coleção. Fotos de perfil podem ser lidas por usuários autenticados e aparecem em tempo real nos avatares; os perfis privados continuam restritos ao titular. Não se usa Storage ou link público externo.

A lista de conversas preserva os documentos em um ViewModel ao trocar de tela e não troca dados existentes por um snapshot inicial vazio de cache. Os checks aparecem antes da prévia apenas para mensagens próprias. As listas usam linhas compactas sem cards por usuário.

Segurar uma mensagem própria abre editar/apagar para todos/apagar para mim/cancelar. Edições mantêm criação e recibos e mostram indicação de edição. Apagar para todos remove o texto, deixa um marcador e tenta cancelar o aviso nos aparelhos por um evento FCM. Apagar para mim oculta apenas para aquela conta. Excluir conversa na lista registra um corte de histórico por usuário; não elimina o histórico do outro participante. Uma nova mensagem faz a conversa reaparecer. Essas ocultações são de apresentação, não apagamento físico dos documentos; apagamento para todos remove o conteúdo textual do documento no servidor.

## Envio e notificações

1. O Android manda o texto e um UUID estável ao Worker com um ID token Firebase.
2. O servidor verifica assinatura, projeto, validade, provedor Google e e-mail confirmado. Aplica limites por usuário; não aceita UID ou destinatário declarados pelo cliente como autorização.
3. Uma transação no Firestore grava a mensagem, o resumo da conversa e um aviso na `notificationOutbox`.
4. O Worker tenta enviar imediatamente pelo FCM. O Android não precisa ficar aberto para o processamento do servidor continuar.
5. Um Cron Trigger a cada minuto retoma avisos pendentes, com lease, espera progressiva e registro dos dispositivos já processados. A rotina não faz uma nova cópia da mensagem.

"Enviada" significa armazenada no servidor, não lida ou recebida pelo aparelho. Uma tentativa repetida com o mesmo UUID não duplica a mensagem. Push tem entrega pelo menos uma tentativa com retomadas; não é uma garantia de exatamente uma entrega no aparelho. Se houver falha entre o FCM aceitar um aviso e o registro da confirmação, ele pode ser reenviado. O mesmo identificador de notificação ajuda a evitar cartões duplicados.

As notificações mostram o remetente e o texto, com expansão. Textos longos têm prévia limitada a 1.200 bytes UTF-8 mais reticências para respeitar o limite do FCM; o texto completo permanece na conversa. A visibilidade na tela bloqueada continua sujeita à privacidade do Android. Tokens FCM ficam em coleção privada. Rotação/registro de token é retomado pelo WorkManager se faltar conexão; o job fica vinculado à conta e é cancelado na saída. Cada conta aceita até 20 instalações ativas no registro do servidor; registros com mais de 60 dias podem ser removidos na próxima atualização.

Mensagens próprias exibem um check cinza (`#8696A0`) após gravação no servidor, dois cinza quando o destinatário confirma recebimento pelo push ou pela consulta da conversa e dois azuis (`#53BDEB`) após exibição na conversa em primeiro plano e com foco. Confirmações são persistidas pelo WorkManager e enviadas em lotes de até 50; o servidor aceita apenas participantes, ignora confirmações do próprio remetente e mantém datas sem regressão. Aceitação pelo FCM não é confirmação de entrega. Aparelhos com versão antiga ainda não enviam confirmações.

O som padrão é o arquivo `icq_uh_oh.mp3`, incluído no app. Como no Alertou, a reprodução usa `USAGE_ALARM`, pelo volume de alarme, inclusive no silencioso/vibração. O canal não toca um segundo som. O FCM envia dados com prioridade alta para o serviço Android publicar o aviso e reproduzir o áudio também em segundo plano. Não há alteração automática do volume ou do Não perturbe. Notificações bloqueadas ou o canal desativado impedem a reprodução; o volume de alarme em zero, o Não perturbe e restrições do aparelho podem silenciá-la. A nova versão Android deve estar instalada para esse protocolo de recebimento.

Em Ajustes → Sons das mensagens, o seletor segue o Alertou: personalizados separados, opções incluídas em duas colunas, seleção imediata, botão “+”, remoção com confirmação e barra de volume da prévia. Há oito opções ICQ (original, suave, agudo, grave, rápido, calmo, brilhante e encorpado) e dez toques curtos originais (pop, duplo, gota, ping cristalino, sino leve, estalo, bolha, sinal digital, subida e descida), sem copiar gravações do WhatsApp. A prévia usa o volume de mídia, sem alterar o volume de alarme das notificações. Arquivos personalizados compatíveis de até 10 MB são validados e copiados para o armazenamento privado; apagar o original não afeta a cópia e remover a cópia não apaga o original. A seleção é local ao aparelho. Os dez WAV são reproduzíveis com `node scripts/generate-message-sounds.cjs`.

O ícone mantém a base verde do Alertou, com contorno, sombra e relevo no símbolo de conversa/F. Os ícones adaptativos continuam separados em fundo e primeiro plano; o fallback antigo tem cantos transparentes.

Sem rede, com avisos desativados, após "Forçar parada" ou sob restrições do Android, notificações podem atrasar ou não aparecer. As mensagens armazenadas continuam disponíveis quando a pessoa voltar ao app. A fila melhora a retomada; não elimina os limites do sistema, dos provedores ou das cotas gratuitas.

## Proteção e limites

- Regras do Firestore permitem leitura de conversas somente aos participantes autenticados por Google e do perfil privado somente ao titular. Miniaturas de perfil podem ser lidas por usuários autenticados, sem listagem da coleção.
- Clientes não podem escrever diretamente em nenhuma coleção. Diretório, dispositivos, quotas e fila são acessíveis apenas pelo servidor.
- O diretório usa hash do e-mail como chave privada de busca. Hash não equivale a anonimização.
- Cache das conversas somente em memória; backup/transferência automática Android desativados.
- Não há criptografia de ponta a ponta nesta versão; isso é informado em Ajustes.
- Até 4.000 unidades UTF-16 por mensagem. Histórico em lotes de 50 e até 100 conversas recentes na lista.
- Quotas por usuário/hora: 600 envios/tentativas, 6.000 e-mails consultados, 100 aberturas de conversa, 20 alterações de perfil e 120 registros de dispositivo.
- Foto: 20 alterações/hora. Edição/exclusão de mensagens: 600 ações/hora. Exclusão de conversa: 100 ações/hora. Confirmações: até 12.000 IDs/hora.
- App Check permanece disponível no Android (debug provider em debug e Play Integrity em release), mas ainda não é exigido pelo Worker. Ativar enforcement só após cadastrar e testar os ambientes; autenticação Google, autorização e quotas já são exigidas no servidor.
- Não há grupos, chamadas, mídia, bloqueio/denúncia, exclusão de conta ou publicação em loja. São pendências de produto para uma distribuição pública.

## Desenvolvimento e validação

JBR/JDK compatível com AGP 8.10.1 e Gradle 8.11.1. SDK 36 e mínimo Android 7 (API 24).

```powershell
# Compilação Java/recursos e análise, sem gerar APK nem executar em Android.
.\gradlew.bat :app:compileDebugJavaWithJavac :app:compileReleaseJavaWithJavac :app:lintDebug

# Testes do Worker (JWT, validações, protocolo Firestore).
npm.cmd --prefix worker ci
npm.cmd --prefix worker test

# Regras, transações e retomada da fila com banco local fictício.
npx.cmd --yes firebase-tools@14.22.0 emulators:exec --only firestore --project demo-falai "npm --prefix worker run test:integration"

# Preparação local do pacote Worker, sem publicação.
npx.cmd --yes --package=node@22.16.0 --package=wrangler@4.50.0 wrangler deploy --dry-run --config worker/wrangler.jsonc
```

A conta de serviço não é usada pelos testes: o emulador usa o projeto fictício `demo-falai`. Os testes de fila usam um envio FCM falso e não entregam notificações a ninguém.

## Publicação

```powershell
# Projeto Firebase explícito; somente regras e índices, sem Functions.
npx.cmd --yes firebase-tools@14.22.0 deploy --only firestore:rules,firestore:indexes --project falai-4d04c

# Worker existente, preservando a variável e o segredo cadastrados no painel.
npx.cmd --yes --package=node@22.16.0 --package=wrangler@4.50.0 wrangler deploy --config worker/wrangler.jsonc
```

O endpoint público `GET /health` verifica OAuth, leitura do Firestore e `validateOnly` do FCM. Não envia notificações e não retorna chaves ou tokens. O resultado saudável é `{ "service": "Falai", "version": "falai-worker-1", "database": "ok", "fcmValidation": "ok" }`. A verificação é armazenada em memória por cinco minutos por instância para reduzir chamadas.

A rotina Cron pode demorar alguns minutos para propagar após a publicação. O envio imediato usa o Worker da requisição e não espera o Cron.

Compilação e testes locais não confirmam aparência, login Google no aparelho ou entrega real com o app fechado. Esse teste final exige autorização para usar os aparelhos.
