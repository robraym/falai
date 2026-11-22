# Configuração Firebase e Cloudflare do Falai

## Dados confirmados nesta configuração

- Firebase: `falai-4d04c`; Android: `br.com.falai`.
- Firestore: `(default)`, Standard, `southamerica-east1`, cota gratuita.
- Login Google ativado pelo responsável pelo app.
- JSON atualizado em `app/google-services.json`, com cliente OAuth Web e SHA-1 do debug cadastrado.
- FCM HTTP V1 ativado no console.
- Worker `falai-notificacoes` criado no plano Free, com `FIREBASE_PROJECT_ID` e o Secret `FIREBASE_SERVICE_ACCOUNT` cadastrados no painel.
- Conta de serviço `falai-worker`, com papéis Firebase Cloud Messaging API Admin e Cloud Datastore User.

O projeto Android agora usa esse Worker no lugar das Cloud Functions. Não é necessário upgrade para Blaze ou cadastro por SMS.

## Certificados do debug deste computador

SHA-1:

```text
60:99:C8:01:44:1E:0B:AD:44:E9:E1:47:57:5E:6F:52:00:94:87:33
```

SHA-256:

```text
1A:BB:D9:8A:CF:90:3F:AF:F2:B5:0F:AF:7C:D0:21:B6:1A:1C:EA:2D:44:56:69:55:7C:2B:6E:08:95:06:AB:8A
```

SHA-1 foi conferido no Firebase durante a configuração. O SHA-256 e App Check de produção devem ser cadastrados e validados antes de exigir enforcement. A assinatura release deve ser escolhida e seus certificados cadastrados antes da distribuição.

## Credencial do servidor

O Secret `FIREBASE_SERVICE_ACCOUNT` contém a chave privada. Não colocar esse JSON na pasta do aplicativo, no Git ou no chat. Excluir a cópia local não revoga o segredo na Cloudflare; excluir a chave no Google Cloud revoga a credencial. Uma troca de chave exige atualizar o Secret do Worker.

## Conferência após publicação

Acesse `https://falai-notificacoes.robraym-studio.workers.dev/health`. Esse endpoint testa acesso ao Firestore e validação FCM sem entregar avisos reais. O serviço ainda precisa de teste em Android com duas contas para confirmar login, agenda, texto e recebimento de notificações.

As instruções de desenvolvimento, publicação, cotas e limitações estão no README principal.
