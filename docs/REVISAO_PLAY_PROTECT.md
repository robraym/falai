# Revisão do Falai e do Play Protect — 23/11/2026

## Resultado

A revisão estática não identificou comportamento malicioso nas rotinas examinadas. Não determina o motivo da classificação do Google e não substitui análise dinâmica/antimalware. As capturas não identificam o hash do APK bloqueado; não foi possível confirmar que correspondem ao arquivo local inspecionado.

O APK local anterior era debug (`1.3`, código `217642041`), com certificado Android Debug válido. A tarefa de publicação também gerava debug. Corrigido o fluxo para gerar a variante release, com depuração desativada, R8 e redução de recursos. A publicação agora rejeita debug, assinatura inválida e certificado diferente do distribuído.

## Assinatura e compatibilidade

O release mantém a chave local já usada nas instalações anteriores, por meio de `signingConfigs.debug`. Portanto, é um build release, mas o certificado continua sendo o Android Debug original; não foi criada uma nova identidade de produção. Isso preserva a compatibilidade de assinatura com o APK anterior analisado. Não é uma confirmação de aceitação pelo Play Protect ou Play Store. Trocar essa chave exige planejar a migração e atualizar o cadastro do Firebase.

SHA-256 do certificado: `1abbd98acf903faff2b50faf7cd021b61a1cea2d445669557c2b6e089506ab8a`.

## Verificações

- Manifesto efetivo do APK: não solicita SMS, acessibilidade, leitura das notificações de outros apps, localização, câmera, microfone ou sobreposição de tela. Solicita contatos e instalação de pacotes, além das permissões de rede/notificações e componentes das bibliotecas.
- Atualizador: APK obtido do repositório fixo do Falai no GitHub; confere tamanho, SHA-256 quando disponível, pacote, versão e certificado. Copia para armazenamento privado e compartilha por `FileProvider` não exportado, limitado à pasta de atualizações. O instalador exige ação do usuário e confirmação do Android. `REQUEST_INSTALL_PACKAGES` tem uso identificado; sua presença não comprova a causa do alerta.
- Contatos: consulta e-mails da agenda após a permissão, com explicação prévia sobre a consulta no servidor. Nomes e telefones da agenda não são enviados por essa rotina. Transporte das chamadas examinado usa HTTPS. A revisão não foi uma auditoria completa de retenção/logs de todos os serviços hospedados.
- Presença: o código registra atividade quando o Falai está em primeiro plano e o aparelho está desbloqueado; não monitora continuamente outros apps. A visibilidade e a explicação dessa informação para o usuário merecem revisão de privacidade separada, sem atribuir a elas a classificação do Google.
- Não foram encontrados carregadores dinâmicos de DEX, execução de comandos de sistema, navegador com ponte JavaScript ou download de código executado diretamente nas rotinas Java pesquisadas. O APK contém bibliotecas nativas `libdatastore_shared_counter.so`; isso não foi tratado como indício de malware.
- APK anterior: assinatura v2 válida, alinhamento válido, sem caminhos ZIP duplicados ou com travessia e sem nomes de arquivos de chaves privadas identificados. Essas verificações não provam ausência de malware.
- Árvore de dependências resolvida inspecionada: famílias AndroidX, Firebase, Google Identity/Credentials e Guava. Não foi feita auditoria completa dos binários de terceiros nem varredura de CVEs. Não foi enviado APK a scanners externos.

## Release gerado e validação

- Arquivo: `app/build/outputs/apk/release/Falai.apk`.
- Versão: `1.3`; código: `217642297`.
- SHA-256: `9a91d1767b02f3001303beedd777206254716cc129cd8c4e84845d45d5632522`.
- `assembleRelease` e `lintRelease` concluídos: zero erros e 51 avisos. A biblioteca nativa DataStore foi empacotada sem remoção de símbolos, conforme aviso do build.
- Prévia da publicação confirmou os metadados, ausência de `application-debuggable`, assinatura válida e certificado esperado. Alinhamento verificado. Sequência Gradle conferida com `--dry-run`: `assembleRelease` precede a publicação.
- O App Check usa Play Integrity em release, conforme configuração já existente. Login, Firestore, notificações e obtenção de atestado no release não foram testados em aparelho. Não houve instalação, publicação de Release, envio ao Git ou alteração em configurações do Firebase.

## Próximo passo quanto ao bloqueio

Gerar release é uma correção de distribuição, não uma garantia de remover o alerta. Caso persista, identificar o APK exato bloqueado e a classificação detalhada e solicitar revisão ao Google após conferir a conformidade. Não orientar usuários a desativar o Play Protect.

Referências oficiais: [avisos e recurso do Play Protect](https://developers.google.com/android/play-protect/warning-dev-guidance), [princípios de software indesejado](https://developers.google.com/android/play-protect/mobile-unwanted-software), [assinatura de aplicativos](https://developer.android.com/studio/publish/app-signing).
