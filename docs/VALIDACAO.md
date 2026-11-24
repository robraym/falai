# Validação da adaptação gratuita

## Atualização em 23/11/2026

### Repositório renomeado para robraym/falai

- Remoto origin, consulta de Releases, publicador e documentação atualizados. URLs antigas só permanecem na validação de downloads pendentes para permitir retomada após atualizar o app.
- Confirmados acesso Git e permissão de publicação no novo repositório. A API nova responde HTTP 200; a antiga retorna HTTP 301 e os APKs anteriores não seguem redirecionamentos nessa consulta, exigindo atualização manual de migração. Nenhuma Release publicada ou push realizado.

### Aviso de mensagens temporárias na conversa

- A janela da foto permanece somente com os dados do perfil. Adicionado menu ⋮ na conversa com Mensagens temporárias e a duração atual, além do relógio na foto quando o prazo está ativo.
- Avisos centralizados continuam no histórico, com toque para explicar/alterar o prazo. O estado do documento da conversa agora supre eventos ausentes, atrasados ou indisponíveis, sem duplicar o evento correspondente e sem inventar ativação em conversa desativada. Eventos de padrão inicial têm explicação própria; falhas de leitura do histórico registram apenas o código do erro.
- O padrão em Ajustes continua valendo somente para conversas novas; não houve mudança de prazos, retroatividade ou escrita de mensagens/avisos pela correção. Backend e regras não foram alterados. Referência: https://faq.whatsapp.com/673193694148537/.
- Compilação Java, 14 testes JVM (cinco cenários novos de avisos) e lint debug aprovados: zero erros, 51 avisos. Sem APK novo, instalação, teste visual em aparelho, publicação ou envio ao Git.

### Release e revisão do Play Protect

- Fluxo `publicarAtualizacao` alterado de debug para release; assinatura original mantida para atualização das instalações anteriores. Depuração desativada, R8/redução de recursos ativos e publicação com conferência de assinatura/certificado. Certificado permanece sendo o Android Debug original, conforme explicado em `REVISAO_PLAY_PROTECT.md`.
- `assembleRelease`, `lintRelease` (zero erros, 51 avisos), assinatura, alinhamento, prévia do script e sequência Gradle validados. Release 1.3 gerado, sem instalar, publicar ou enviar ao Git. Revisão estática detalhada em `REVISAO_PLAY_PROTECT.md`; causa do bloqueio não confirmada.

### Publicação pelo Android Studio

- Adicionadas a configuração de execução `Publicar atualização` e as tarefas Gradle `publicarAtualizacao`/`verificarLoginPublicacao`. O script usa o Git Credential Manager em memória, lê a versão real pelo aapt, confere metadados e só publica após o upload do APK em rascunho. Não substitui assets nem faz push do código.
- Verificados o login e a permissão de escrita no repositório real, a prévia com o APK existente e a sequência completa com `--dry-run`. Nenhuma Release/tag criada, nenhum APK novo gerado e nenhuma instalação/envio ao Git nesta configuração. O upload e a publicação final não foram executados.

### Atualização com download interno

- Interface de verificação/download/instalação unificada em painel inferior inspirado na One UI: cartões de versão, porcentagem destacada, barra arredondada e botões largos. Conteúdo rolável com altura limitada, respeitando tema e cor do aplicativo; a janela se adapta às mudanças de estado. Compilação Java, nove testes JVM e lint concluídos (zero erros, 41 avisos). Aparência ainda não validada em aparelho/emulador; sem instalação e sem envio ao Git.
- Substituído o link aberto no navegador por `DownloadManager`, com progresso/tamanho, cancelamento, descarte, nova tentativa, download em segundo plano e retomada pela tela de atualização. O identificador/metadados são persistidos; a janela é restaurada ao recriar a Activity. Consultas e validação rodam fora da thread da interface.
- Integridade: tamanho do asset, SHA-256 quando publicado, pacote, nome/código da versão e assinatura compatível. A cópia para o instalador é privada, distinta do arquivo externo do download; `FileProvider` não exportado compartilha somente a pasta interna de atualizações e concede leitura temporária para a URI escolhida. Falta de espaço e assinatura diferente têm mensagens próprias.
- `REQUEST_INSTALL_PACKAGES` e autorização por origem a partir do Android 8, com retorno pelo Activity Result; instalação só é aberta após ação do usuário e com tela em primeiro plano. A confirmação final continua no Android. Não há desinstalação ou limpeza de dados do app.
- Compilação Java debug, nove testes JVM de integridade/identidade/assinatura e lint aprovados após a última alteração (zero erros, 40 avisos). Testes cobrem corrupção de mesmo tamanho, arquivo truncado, hash ausente/inválido, pacote/versão incorretos, certificado incompatível e rotação/múltiplos signatários. Não foi gerado/instalado APK, nem realizado download ou instalação em aparelho/emulador. Sem envio ao Git.

### Mensagens temporárias compartilhadas — substitui a limpeza local descrita nas etapas anteriores

- Prazo padrão por conta para novas conversas e prazo compartilhado por conversa, alterável por qualquer participante. A escolha do criador é usada ao abrir uma conversa; se desativada, é utilizada a da outra pessoa. Reabrir uma conversa não reaplica o padrão. Alterações recebem UUID idempotente e geram eventos privados dos participantes. O aviso abre diretamente a seleção, com explicação e gravação ao tocar na opção; o perfil do cabeçalho permite abrir as opções mesmo sem aviso.
- `expiresAt` gerado no servidor por mensagem, imutável após troca/desativação. Preservados textos permanentes anteriores e datas das temporárias anteriores. Clientes filtram expiradas, notificações usam validade e o cron apaga gradualmente o texto, conservando metadados para bloquear repetição. A fila de limpeza processa duas entradas por execução. Contadores descontam baldes de expiração por minuto sem transformar expiração em leitura.
- Consulta do histórico distingue o legado anterior à primeira configuração e mensagens novas ainda não limpas, com índice `expired/createdAt`; assim os marcadores de expiração não ocupam indefinidamente as páginas do histórico. Cursor de paginação continua usando o documento, separado dos avisos de sistema.
- Compilação Java e lint aprovados (zero erros, 40 avisos). 14 testes unitários e 24 de integração passaram: validade das três durações, confirmação por ambos os remetentes, mudança/desativação pela outra pessoa, preservação de mensagens anteriores, repetição concorrente, autorização de avisos/padrões, prazo padrão só em novas conversas, bloqueio de push expirado, remoção de texto, preservação do legado e contadores com recibos atrasados. Sem instalação ou teste visual em aparelho.
- Regras e índice publicados; índice de mensagens confirmado como `READY` e a consulta real equivalente à do Android passou em leitura de uma conversa inexistente, sem criar dados ou ler conteúdo de conversas reais. Worker publicado como `233b6c84-be69-40e7-878f-258e287f18dd`; `/health` confirmou banco e FCM. Nenhuma notificação real enviada.

- Apresentação do aviso de duração refeita: saiu do cabeçalho e passou a ser uma linha centralizada da lista, com relógio, texto curto e detalhes ao tocar. O controlador mantém os dois observadores somente enquanto a Activity está iniciada e apresenta um aviso da configuração atual por participante, ordenado pela data de atualização. Sem novos eventos ou escritas no servidor.
- O adaptador agora distingue mensagens de avisos: `at()` retorna nulo para aviso, preservando confirmações de leitura; separadores de data ignoram avisos intermediários e a busca mostra somente mensagens. Paginação continua usando o documento mais antigo, sem utilizar avisos como cursor.
- Compilação Java e lint aprovados após a alteração (zero erros, 41 avisos). Fluxos de posicionamento, busca, recibos e paginação revisados no código; sem instalação, execução ou captura de tela em aparelho/emulador. Mudança local, sem envio ao Git.

- Aviso de duração no cabeçalho do chat para os dois participantes: identifica quem configurou e quais prazos estão ativos; mostra desativação e explica o alcance local. Dois observadores de documentos são ligados somente enquanto a tela está anexada/visível; não há push real nem mensagem inserida no histórico por essa divulgação.
- Registro de dispositivo divulga o prazo salvo e a revisão local. O servidor mantém até 20 registros privados de configuração e publica somente a união de durações e a data da alteração. Reenvios antigos não sobrescrevem configurações novas, e aparelhos antigos sem esses campos não apagam a divulgação. Alterações da preferência tentam sincronização imediatamente, com retomada pelo `DeviceRegistrationWorker` em falha.
- Compilação Java e lint aprovados (zero erros, 40 avisos), 12 testes unitários e 21 de integração aprovados. Integração cobre múltiplos aparelhos, desativação, revisão antiga, ausência de dados privados, acesso autenticado e bloqueios de listagem/escrita. As oito verificações locais da política de duração também passaram após incluir a revisão. Sem instalação, abertura ou teste visual em aparelho.
- Regras de divulgação publicadas e Worker atualizado para `19e7fb27-9afe-4a18-bad3-0ed220b57a44`; `/health` confirmou banco e validação FCM.

- Duração local das conversas: Desativado/24 horas/7 dias/90 dias, por conta e aparelho, com confirmação antes de aplicar ao histórico atual. Limite monotônico salvo; ampliação/desativação preservam o corte anterior. Limpeza da visualização, sem apagar documentos no Firestore ou marcar mensagens como lidas. A aplicação usa o horário do aparelho; desinstalar/limpar os dados pode remover a preferência.
- Atualização das telas ao retornar e a cada minuto, sem recriar a navegação. Query do histórico limitada pelo corte local e avisos expirados cancelados. Push inclui data original para impedir que uma entrega atrasada gere aviso após o prazo local.
- Compilação Java e lint aprovados novamente (zero erros, 40 avisos), 12 testes unitários do Worker aprovados após adicionar a data original ao push. Verificação Java local da classe real `HistoryRetention` com substitutos mínimos das APIs Android passou em oito cenários: padrão desativado, três durações, ampliação/desativação sem restauração, isolamento e preservação por conta. Essa verificação não substitui teste de interface em aparelho. Nenhum APK instalado/aberto.
- Worker final publicado como `dc9caa18-0927-488c-a57d-9c6cc72cb1ec`; `/health` confirmou banco e FCM. Regras de nomes públicos já publicadas na etapa anterior. Sem envio ao Git nesta etapa.

- Nome público sincronizado por `profileNames/{uid}`, com leitura autenticada de documento individual e sem listagem/escrita direta. Atualização do perfil publica o nome na mesma transação; clientes observam somente linhas/cabeçalhos visíveis. Nome do perfil priorizado sobre a agenda e nome atual utilizado no push. Integração valida alteração, descoberta, ausência de e-mail e bloqueios de acesso/escrita.
- Limpar conversa conserva a linha e oculta o histórico anterior apenas para o usuário; Excluir conversa conserva o comportamento anterior. Ambas as opções exigem confirmação. Teste cobre autorização, contador de não lidas, preservação do histórico e exclusão após limpeza.
- Compilação Java e lint aprovados (zero erros, 40 avisos); 12 testes unitários e 20 de integração aprovados. Regras do Firestore publicadas e Worker `2d1a077f-7ee1-40d8-932e-ac990815e025` implantado, com banco/FCM saudáveis no `/health`. Nenhuma instalação, abertura em aparelho ou notificação real de teste.

- Intervalo do Chamar atenção reduzido para dez segundos por remetente/conversa; limite de 20 chamadas por hora preservado. Texto do aviso atualizado. Os 18 testes de integração passaram, incluindo bloqueio aos cinco segundos e liberação após onze segundos. Worker publicado como `2c386b9b-56ed-4483-abb0-ce3c1572a0f3`, com `/health` saudável. Nenhum teste em aparelho nem envio real de notificação.

### Foto na notificação, não lidas e mais cores

- Foto circular do remetente no `MessagingStyle` e no ícone grande; inicial quando indisponível, cache por dez minutos e busca limitada a dois segundos após publicar o aviso. Atualização do mesmo aviso sem repetir áudio e somente se ainda estiver ativo. O push inclui o UID do remetente obtido da mensagem no servidor.
- Contagens de não lidas no resumo de cada conversa, atualizadas nas transações já existentes. Leituras parciais, repetição de recibos/envios, respostas, chamadas de atenção, exclusão para todos/para si e recibos antigos após limpar a conversa estão cobertos na integração. A contagem de mensagens novas não exige consultas extras ao histórico. Resumos legados exibem um ponto para a última recebida ainda não lida; não houve migração de todo o histórico antigo.
- Notificações das mensagens visualizadas são canceladas após a confirmação de leitura. A prévia fica em negrito e a hora recebe a cor do aplicativo enquanto houver pendências.
- Paleta ampliada de seis para 30 cores, preservando os índices anteriores, com estilos de diálogo e variações claras/escuras correspondentes. Seletor ocupa 90% da altura disponível, com grade rolável, amostras e prévia; Fechar permanece fora da rolagem.
- Compilação Java debug e lint aprovados (zero erros, 40 avisos). Os 12 testes unitários e 18 testes de integração passaram. Nenhum APK foi instalado/aberto e nenhuma notificação real de teste foi enviada; aparência e comportamento no aparelho ainda não conferidos.
- Worker atualizado para `9df7213a-3248-4d51-9246-027bd85ca909`; `/health` confirmou banco e validação FCM sem entrega real.

- Falha de abertura confirmada no crash log do S23 Ultra: `MessageSoundPicker` lia o volume via `SharedPreferences` durante a construção de `MainActivity`, antes de `Activity.attach()`. A leitura ficou apenas em `show()`, com o contexto disponível. Compilação debug e lint passaram após a correção; a abertura da versão corrigida ainda precisa ser conferida no aparelho. Nenhum cache ou dado do usuário foi apagado durante a investigação.
- Volume da barra salvo por aparelho e aplicado às mensagens e ao Chamar atenção; prévia e alertas usam a mesma categoria de áudio de alarme. Aparência agora tem opções diretas de cor e tamanho, com fontes e recuos das linhas padronizados; Privacidade foi retirada dos Ajustes.

- Versão 1.1 (código 2): troca da data de geração pela versão registrada, com validação no Worker e fallback para versões antigas.
- Chamar atenção: mensagem autenticada, deduplicação, cooldown por conversa/remetente, limite por hora, validade de 60 segundos no push, som MSN separado e animação curta apenas no Falai em primeiro plano. Casos de edição, exclusão, repetição concorrente, abuso e expiração cobertos no servidor.
- 29 testes locais aprovados (12 unitários e 17 de integração com Firestore). Compilação debug e lint aprovados, com 0 erros e 34 avisos. Áudio, animação e espaçamento ainda não conferidos em aparelho; nenhuma instalação, abertura ou notificação real de teste foi feita.
- Margens e recuos laterais reduzidos, preservando a aplicação dos insets do sistema.
- Worker com chamadas de atenção e versão do APK publicado como `38b74de5-07fd-4de8-86d0-87dd636393a5`; `/health` confirmou banco e FCM em modo de validação, sem envio de aviso real.

- Seletor de cores: amostras circulares, grade de duas colunas, prévia e aplicação ao fechar, conferidos contra o código do Alertou.
- Contagem de Contatos em selo; navegação selecionada com destaque arredondado atrás do ícone e título em negrito. Papel de parede em todas as telas, sem contorno externo dos cards ou divisores horizontais.
- Ajustes mostra Cor do aplicativo, Tamanho dos textos, Sons das mensagens e Sair da conta diretamente. Notificações e Privacidade foram removidas.
- Investigação por ADB, somente leitura, no S23 Ultra (`SM_S918B`): canal `messages_icq_v1` com `mImportance=4`, `mSound=null`, `mVibrationEnabled=false` e `mUserLockedFields=0`.
- Nova implementação usa `messages_alerts_v2`, vibração e `MessagingStyle`, mantendo áudio separado e respeitando a importância/bloqueio do canal antigo. Nenhuma notificação foi disparada durante a investigação.
- Compilação Java debug e lint aprovados após a última edição visual (0 erros, 34 avisos). Não houve instalação, execução ou captura em Android; confirmação visual e acendimento da tela do relógio permanecem pendentes.

## Conferências anteriores

Conferência em 22/11/2026.

Ampliação final dos sons: 18 opções (8 variações ICQ e 10 toques curtos), com `compileDebugJavaWithJavac` aprovado após a última edição. Os dez WAV totalizam aproximadamente 165 KiB, duram de 0,14 a 0,60 segundo e foram gerados sem saturação. As verificações auditivas e de interação em aparelho continuam pendentes.

Sons/ícone/lupa: `compileDebugJavaWithJavac` e `lintDebug` aprovados após adicionar o seletor e os recursos do ícone (0 erros, 27 avisos). A posição inicial da lupa interna foi revisada no código depois dessa verificação. Os três WAV foram gerados como PCM mono de 22.050 Hz, com picos abaixo de 0,9 e tamanho total inferior a 50 KiB. Não houve teste auditivo, instalação ou execução em aparelho. A importação, seleção e remoção de personalizados ainda precisam de conferência no Android.

Atualização desta implementação: compilação Java debug aprovada após as mudanças de busca, presença, digitação, data do APK, balões e seletor de emojis. Conferência da lista preservou os 120 emojis, sem duplicações, em seis categorias. Não houve instalação ou execução em Android. Release/lint abaixo pertencem à conferência anterior.

Os 27 testes locais passaram (12 unitários e 15 de integração no emulador do Firestore). Os dois novos testes verificam propriedade e permissões de presença/digitação, rejeição de timestamps forjados, data pública do APK sem tokens e compatibilidade com clientes antigos. Regras publicadas; Worker atualizado para `8ccdb5bd-93de-42bd-9e37-bb5837c37fc3`.

## Android

- `compileDebugJavaWithJavac`: passou.
- `compileReleaseJavaWithJavac`: passou.
- `lintDebug`: passou, com 0 erros e 22 avisos (versões de dependências, recursos antigos sem uso e sugestões de ícone/layout/acessibilidade).
- JSON Firebase atualizado, cliente OAuth Web presente e pacote `br.com.falai`.
- Não foi gerado APK e não houve instalação, execução ou captura de tela em Android.

## Testes locais

**Conferência anterior: 25 testes passaram:** 12 de autenticação/validação/protocolo e 13 de integração com o emulador local do Firestore.

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

### Refinos de layout e emojis

- Cabeçalho compacto, com a contagem de Contatos ao lado do título para manter as listas na mesma altura. Ícone de perfil fixado no topo.
- Ajustes em lista com divisores, sem cards por seção. Recado do Perfil alinhado à esquerda, com título discreto e lápis separado à direita.
- Seletor ampliado de 20 para 120 emojis únicos, preservando os anteriores, com rolagem e colunas adaptáveis. Seleção baseada na tabela pública de frequência do Unicode, sem alegar ranking ao vivo.
- Compilação debug e revisão do diff passaram antes do envio ao Git. Sem instalação ou teste visual em aparelho.

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
