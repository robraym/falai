# Validação da adaptação gratuita

Conferência em 22/11/2026.

## Android

- `compileDebugJavaWithJavac`: passou.
- `compileReleaseJavaWithJavac`: passou.
- `lintDebug`: passou, com 0 erros e 22 avisos (versões de dependências, recursos antigos sem uso e sugestões de ícone/layout/acessibilidade).
- JSON Firebase atualizado, cliente OAuth Web presente e pacote `br.com.falai`.
- Não foi gerado APK e não houve instalação, execução ou captura de tela em Android.

## Testes locais

**25 testes atuais passaram:** 12 de autenticação/validação/protocolo e 13 de integração com o emulador local do Firestore.

Cobertura relevante: assinatura JWT, projeto e provedor Google corretos, e-mail verificado, validação de texto e emoji, privacidade das conversas, bloqueio de escrita direta, diretório privado, limites de uso, envio concorrente com o mesmo UUID, gravação atômica do aviso pendente, falha parcial de push, retomada sem repetir os dispositivos já confirmados e recuperação de lease vencido.

Os erros de permissão exibidos durante os testes são esperados nos casos em que o acesso deve ser negado. Testes de FCM usam envio falso; não houve entrega de avisos a pessoas ou aparelhos.

## Publicação e conferência online

- Regras e índices publicados no Firestore do projeto `falai-4d04c`.
- Worker publicado em `https://falai-notificacoes.robraym-studio.workers.dev`.
- Versão Cloudflare: `7642c02e-e1a9-4ec0-90c2-e03235c60870`.
- Secret existente preservado no Worker; chave privada não foi copiada para o projeto ou o APK.
- `/health` retornou HTTP 200, `database: ok` e `fcmValidation: ok`.
- A conferência do FCM usa `validateOnly`: verifica a credencial sem enviar uma notificação.
- Consulta real da fila com seu índice composto passou.
- Requisição sem autenticação retornou HTTP 401.
- Requisição com assinatura JWT falsificada, usando um identificador real de chave pública Google, retornou HTTP 401.
- Cron a cada minuto publicado; uma execução real foi observada com resultado `Ok`, sem falha na consulta da fila vazia.
- Nenhum comando de upgrade, ativação de faturamento ou publicação em Git foi executado.

## Limites da conferência

### Recado público do perfil

- Campo Recado editável no Perfil com limite de 139 unidades UTF-16 e contador; salvar vazio remove a frase.
- Exibição em tempo real na lista de contatos e no painel de foto/nome aberto pelo cabeçalho do chat, sem publicar e-mail ou perfil privado.
- `profileAbout`: leitura individual autenticada, sem listagem ou escrita direta. A API grava sempre no UID autenticado e limita a 20 alterações por hora.
- Testados: gravação, normalização, leitura por outro usuário, bloqueio de não autenticados/listagem/escrita direta, rejeição de texto inválido/longo, preservação ao alterar nome e remoção.
- A execução inicial encontrou conflito de transação no teste concorrente de envio; a segunda falha era consequência da ausência da mensagem criada por esse teste. O adaptador foi corrigido para reutilizar o token da transação abortada e aplicar espera variável entre tentativas. O teste unitário de retomada e a suíte completa passaram após a correção.
- Compilação debug/release e lint sem erros. Sem envio de recado real nem execução em aparelhos durante a validação.
- Worker, regras e índices do recado publicados; `/health` retornou HTTP 200 e o novo endpoint sem autenticação retornou HTTP 401.

### Perfil, Aparência, pesquisa e ações de conversa

- Ajuste posterior do Perfil: foto de 112dp centralizada; tocar nela permite escolher/remover; nome editável e e-mail aparecem uma única vez abaixo, sem card ou linha “Foto do perfil”. Essa alteração visual foi revisada no código, sem novo build ou execução em aparelho.

- Ícone de perfil no cabeçalho seguindo o Alertou; tela separada com foto, nome e e-mail somente para leitura. Ajustes permanece ao lado de Contatos, com Aparência e itens de cor/tamanho, notificações, privacidade e saída.
- Seis cores, tamanho de texto 70–150%, tema do aparelho e aplicação nas três atividades. Layout de opções inspirado diretamente nos recursos do perfil do Alertou (ícones circulares, títulos/resumos, divisores e medidas).
- Foto da galeria recortada, orientada com AndroidX ExifInterface, comprimida a JPEG de 192px e até 16 KiB. Servidor valida formato, bytes e dimensões; grava apenas na conta autenticada. Testados: leitura autenticada da foto, bloqueio de listagem e de escrita direta, rejeição de fotos inválidas e preservação após alteração de nome.
- Avatares de perfil, contatos, lista de conversas e cabeçalho do chat usam listeners de documento enquanto visíveis, com cache limitado em memória.
- Pesquisa flutuante reaproveitada do Alertou: arraste, posição salva, expansão/recolhimento, teclado e filtro. Listas sem cards individuais, contagem real de contatos, títulos por tela e ausência de busca manual por e-mail.
- Conversas mantidas em ViewModel por conta; snapshot inicial vazio de cache não apaga a lista. Exclusão local da conversa não volta por causa de cache atrasado e novas mensagens podem fazê-la reaparecer.
- Balões com ponta e rodapé de horário/checks junto do texto. Checks também antes da prévia de mensagens próprias na lista.
- Menus de mensagem: editar, apagar para todos, apagar para mim e cancelar. Menus de conversa: excluir e cancelar, com confirmação. Servidor verifica autoria/participação; edição preserva recibos/criação, exclusão para todos remove texto, exclusão para mim e corte de conversa preservam o histórico do outro usuário. Recibos atualizam o resumo da conversa e há cobertura da migração do resumo antigo.
- Worker/regras/índices publicados. Saúde online retornou HTTP 200 e os quatro novos endpoints recusaram chamadas sem autenticação com HTTP 401. Não foi enviada mensagem ou foto real durante a validação.
- Não houve instalação, abertura ou captura em aparelho; galeria, aparência, gestos, fonte e sincronização real entre S7/S23 ainda precisam de conferência em dispositivos com a versão atualizada.

### Papel de parede e últimos ajustes

- Papel de parede vetorial de desenhos repetidos apenas no histórico da conversa: bege claro e azul-escuro conforme o sistema. Sem imagem externa ou bitmap grande.
- Prévia e data/hora na mesma linha, com reticências na mensagem longa. Removidos os 8dp adicionais entre cabeçalho e conteúdo do card.
- Compilação debug/release e lint verificados antes da publicação no Git. Sem instalação ou captura de tela em aparelho.

### Tema do sistema, cabeçalho e resumo de conversas

- Tema AppCompat DayNight com `MODE_NIGHT_FOLLOW_SYSTEM`, paletas clara/escura do Alertou e ícones das barras do sistema com contraste conforme o modo.
- Cabeçalho reconstruído com a mesma hierarquia XML do Alertou. Solicitação de insets após conexão/troca do conteúdo e reaproveitamento dos insets da janela; contorno limitado abaixo da área de status.
- Lista de Conversas mostra o horário local de `updatedAt` na mesma linha da prévia, à direita, somente quando há mensagem. A prévia ocupa o espaço restante e recebe reticências quando longa. A data usa Hoje/Ontem, dia da semana nos últimos dias e dia/mês para datas mais antigas; ano apenas se diferente do atual. Contatos/Ajustes preservam suas linhas sem esse campo.
- Compilação debug/release e lint sem erros. Não houve teste visual, troca do tema do aparelho ou instalação desta versão.

### Fluxo de nova conversa e composição

- Botão textual de encontrar contatos substituído por botão flutuante verde de 56dp. Abre seleção separada com voltar; ao escolher contato, a seleção é encerrada e a conversa aberta.
- Cabeçalho de 54dp, texto de 27sp e posição derivada do layout/código do Alertou. Contorno com margem de 10dp mais meia espessura, navegação alinhada à largura do card e preenchimento contínuo até a aba selecionada.
- Emoji, entrada de texto e envio na mesma linha; texto multilinha até cinco linhas sem uma segunda faixa de ações.
- Compilação debug/release e lint sem erros. A conferência foi por código e compilação, sem instalação nem validação visual em aparelho desta alteração.

### Confirmações, prévia e ajustes visuais

- Agora são 18 testes atuais: 10 unitários e 8 de integração com Firestore local, todos passaram. Incluem autorização das confirmações, impedimento de confirmação pelo remetente, idempotência, preservação das datas e limite UTF-8 da prévia.
- Checks desenhados por vetores de traço: um/dois cinza `#8696A0`, dois azuis `#53BDEB`. Leitura depende da mensagem visível com conversa retomada e janela com foco.
- Prévia de remetente/texto na notificação, botão de atualização removido e navegação com ícones/títulos centralizados e medidas iguais.
- Paleta verde copiada dos recursos noturnos do Alertou: destaque `#45C98A`, superfícies de ação `#142D22`; fundo escuro e checks azuis preservados.
- Worker publicado e `/health` conferido novamente. Sem instalação ou teste visual desta versão nos aparelhos.

### Som do ICQ

- Arquivo fornecido copiado sem alteração para `app/src/main/res/raw/icq_uh_oh.mp3`; SHA-256 idêntico ao original.
- Reprodução por `MediaPlayer` com `USAGE_ALARM`, como no Alertou. Canal sem som adicional, notificações bloqueadas respeitadas e supressão limitada de repetição por mensagem.
- Worker atualizado para FCM de dados com prioridade alta, permitindo que o serviço Android publique o aviso e toque o áudio em primeiro e segundo plano.
- Os 9 testes unitários atuais passaram, incluindo o contrato de push sem notificação automática duplicada. Os 7 testes de integração anteriores não foram repetidos nesta alteração, que não muda persistência/autorização/fila.
- Compilação debug/release e lint conferidos novamente. Sem execução em aparelho; silencioso, vibração, volume e restrições do fabricante ainda precisam de validação real.

Ainda não foram testados em aparelhos: login Google, aparência, permissão e leitura real da agenda, troca de texto entre duas contas e notificação com o app fechado. A validação online comprova a configuração do servidor e as recusas de autenticação testadas; não comprova entrega real a um dispositivo.

Contato automático passou a usar o e-mail da agenda correspondente à conta Google, com busca manual por e-mail. Não há cadastro por SMS, mídia ou criptografia de ponta a ponta. As cotas gratuitas e as restrições do Android continuam valendo.
