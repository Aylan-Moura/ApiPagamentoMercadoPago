# ApiPagamento

API REST de pagamentos integrada ao Mercado Pago, desenvolvida com Java 17 e Spring Boot 4.0.3. Responsável por criar sessões de checkout, processar confirmações via webhook e persistir o ciclo de vida de cada transação.

---

## Índice

- [Stack](#stack)
- [Arquitetura](#arquitetura)
- [Estrutura de Pacotes](#estrutura-de-pacotes)
- [Endpoints da API](#endpoints-da-api)
- [Modelo de Dados](#modelo-de-dados)
- [Segurança](#segurança)
- [Configuração e Variáveis de Ambiente](#configuração-e-variáveis-de-ambiente)
- [Banco de Dados e Migrations](#banco-de-dados-e-migrations)
- [Deploy (Render)](#deploy-render)
- [Testes](#testes)
- [Desenvolvimento Local](#desenvolvimento-local)
- [Tratamento de Erros](#tratamento-de-erros)
- [Credenciais de Teste — Mercado Pago](#credenciais-de-teste--mercado-pago)

---

## Stack

| Categoria       | Tecnologia               | Versão       |
|-----------------|--------------------------|--------------|
| Runtime         | Java                     | 17 LTS       |
| Framework       | Spring Boot              | 4.0.3        |
| Persistência    | Spring Data JPA          | Hibernate 7  |
| Migrations      | Flyway                   | 11.8.2       |
| Banco           | PostgreSQL                | 15           |
| Pagamentos      | Mercado Pago SDK         | 2.1.24       |
| Documentação    | SpringDoc OpenAPI        | 2.8.6        |
| Observability   | Spring Boot Actuator     | —            |
| Contêiner       | Docker (multi-stage)     | —            |
| Deploy          | Render                   | —            |
| Testes          | JUnit 5 + Mockito        | —            |
| Integração      | Testcontainers           | 1.20.4       |

---

## Arquitetura

A aplicação segue a arquitetura em camadas do Spring MVC com separação estrita de responsabilidades. Controllers são adaptadores HTTP puros — toda a lógica de negócio reside nos Services.

```
Controller  →  Service  →  Repository  →  PostgreSQL
                  ↓
          MercadoPagoService  →  Mercado Pago API
                  ↓
      WebhookSignatureValidator
```

### Fluxo de Pagamento

```
1. Frontend          →  POST /api/checkout
2. API               →  Cria Purchase (PENDING) + preferência no MP
3. Frontend          →  Redireciona usuário para URL do Mercado Pago
4. Usuário           →  Realiza o pagamento
5. Mercado Pago      →  POST /api/webhooks/mercadopago
6. API               →  Valida assinatura HMAC e atualiza status
7. Frontend          →  GET /api/purchases/{id} para exibir resultado
```

### Ciclo de Vida de uma Purchase

```
         POST /api/checkout
                  │
                  ▼
             [ PENDING ]
                  │
     Webhook do Mercado Pago
                  │
      ┌───────────┼───────────┐
      ▼           ▼           ▼
  [APPROVED]  [REJECTED]  [CANCELLED]
```

---

## Estrutura de Pacotes

```
src/
├── main/
│   ├── java/com/aylan/apipagamento/
│   │   ├── ApiPagamentoApplication.java
│   │   ├── config/
│   │   │   ├── DataSourceConfig.java           # Converte DATABASE_URL → JDBC (Render)
│   │   │   ├── MercadoPagoClientConfig.java    # Beans: PaymentClient, PreferenceClient
│   │   │   ├── PostgresUrlCondition.java       # Ativa DataSourceConfig condicionalmente
│   │   │   └── WebConfig.java                  # CORS por variável de ambiente
│   │   ├── controller/
│   │   │   ├── CheckoutController.java         # POST /api/checkout, GET /api/purchases/{id}
│   │   │   └── WebhookController.java          # POST /api/webhooks/mercadopago
│   │   ├── dto/
│   │   │   ├── request/
│   │   │   │   └── CheckoutRequest.java
│   │   │   └── response/
│   │   │       ├── CheckoutResponse.java
│   │   │       ├── ErrorResponse.java
│   │   │       └── PurchaseStatusResponse.java
│   │   ├── exception/
│   │   │   └── GlobalExceptionHandler.java
│   │   ├── model/
│   │   │   ├── Purchase.java
│   │   │   └── PurchaseStatus.java
│   │   ├── repository/
│   │   │   └── PurchaseRepository.java
│   │   └── service/
│   │       ├── MercadoPagoService.java
│   │       ├── PurchaseService.java
│   │       └── WebhookSignatureValidator.java
│   └── resources/
│       ├── application.properties              # Config base (todos os perfis)
│       ├── application-dev.properties          # Dev local
│       ├── application-prod.properties         # Produção (Render)
│       └── db/migration/
│           └── V1__create_purchases_table.sql  # Schema Flyway
└── test/
    └── java/com/aylan/apipagamento/
        ├── controller/
        │   ├── CheckoutControllerTest.java
        │   └── WebhookControllerTest.java
        ├── repository/
        │   └── PurchaseRepositoryTest.java     # Testcontainers + PostgreSQL real
        └── service/
            ├── PurchaseServiceTest.java
            └── WebhookSignatureValidatorTest.java
```

---

## Endpoints da API

A documentação interativa completa está disponível via Swagger UI:

- **Dev:** `http://localhost:3000/swagger-ui.html`
- **Prod:** `https://seu-app.onrender.com/swagger-ui.html`

### Tabela de Endpoints

| Método | Endpoint                       | Descrição                                  | Autenticação |
|--------|--------------------------------|--------------------------------------------|--------------|
| `POST` | `/api/checkout`                | Inicia checkout e retorna URL de pagamento | Público      |
| `GET`  | `/api/purchases/{id}`          | Consulta status de uma compra por ID       | Público      |
| `POST` | `/api/webhooks/mercadopago`    | Recebe notificações do Mercado Pago        | HMAC-SHA256  |
| `GET`  | `/api/return/success`          | Redirect pós-pagamento (aprovado)          | Público      |
| `GET`  | `/api/return/pending`          | Redirect pós-pagamento (pendente)          | Público      |
| `GET`  | `/api/return/failure`          | Redirect pós-pagamento (falha)             | Público      |
| `GET`  | `/actuator/health`             | Health check da aplicação                  | Público      |

---

### POST `/api/checkout`

**Request Body**
```json
{
  "productId": "CURSO-BACKEND-01",
  "email": "comprador@email.com"
}
```

**Response 200**
```json
{
  "checkoutUrl": "https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=..."
}
```

**Response 400** — validação
```json
{
  "timestamp": "2026-03-01T14:30:00Z",
  "message": "Dados inválidos na requisição",
  "fieldErrors": {
    "email": "Formato de e-mail inválido",
    "productId": "O ID do produto é obrigatório"
  }
}
```

**Response 502** — Mercado Pago indisponível
```json
{
  "timestamp": "2026-03-01T14:30:00Z",
  "message": "Serviço de pagamento temporariamente indisponível. Tente novamente em instantes."
}
```

---

### GET `/api/purchases/{id}`

**Response 200**
```json
{
  "id": 42,
  "status": "APPROVED",
  "preferenceId": "1234567890-abc-def-ghi"
}
```

> **Nota LGPD/IDOR:** o campo `email` foi omitido intencionalmente. O endpoint é público e IDs sequenciais são enumeráveis — expor o e-mail permitiria vazar dados pessoais de todos os compradores.

**Códigos possíveis:** `200 OK` | `404 Not Found` | `400 Bad Request` (ID não numérico)

---

### POST `/api/webhooks/mercadopago`

Chamado automaticamente pelo Mercado Pago. Não deve ser invocado pelo frontend.

**Headers obrigatórios**

| Header         | Descrição                                          |
|----------------|----------------------------------------------------|
| `x-signature`  | Assinatura HMAC-SHA256: `ts=<timestamp>,v1=<hash>` |
| `x-request-id` | ID único da requisição gerado pelo Mercado Pago    |

**Request Body**
```json
{
  "type": "payment",
  "data": { "id": "123456789" }
}
```

**Comportamento por cenário**

| Cenário                              | HTTP | Ação                                       |
|--------------------------------------|------|--------------------------------------------|
| Tipo diferente de `payment`          | 200  | Ignorado silenciosamente                   |
| Assinatura inválida ou ausente       | 401  | Rejeitado — Purchase **não** é atualizada  |
| `data.id` ausente                    | 400  | Rejeitado                                  |
| Processamento com sucesso            | 200  | Status da Purchase atualizado              |
| Erro interno                         | 200  | Logado internamente (evita reenvios do MP) |

---

## Modelo de Dados

### Tabela `purchases`

| Coluna         | Tipo          | Nullable    | Descrição                                                   |
|----------------|---------------|-------------|-------------------------------------------------------------|
| `id`           | `BIGSERIAL`   | `NOT NULL`  | Chave primária auto-incremental                             |
| `email`        | `VARCHAR(255)`| `NOT NULL`  | E-mail do comprador — dado pessoal (LGPD)                   |
| `product_id`   | `VARCHAR(255)`| `NOT NULL`  | Identificador do produto                                    |
| `preference_id`| `VARCHAR(255)`| nullable    | ID da preferência no Mercado Pago — **indexado**            |
| `payment_id`   | `VARCHAR(255)`| nullable    | ID do pagamento confirmado via webhook — **indexado**       |
| `status`       | `VARCHAR(50)` | `NOT NULL`  | `PENDING` \| `APPROVED` \| `REJECTED` \| `CANCELLED`       |
| `created_at`   | `TIMESTAMP`   | `NOT NULL`  | Preenchida automaticamente (`@CreationTimestamp`)           |
| `updated_at`   | `TIMESTAMP`   | `NOT NULL`  | Atualizada automaticamente (`@UpdateTimestamp`)             |

### Índices

| Nome                          | Coluna          | Justificativa                                     |
|-------------------------------|-----------------|---------------------------------------------------|
| `idx_purchases_preference_id` | `preference_id` | Consulta por preferenceId em webhooks             |
| `idx_purchases_payment_id`    | `payment_id`    | Deduplicação de notificações                      |
| `idx_purchases_status`        | `status`        | Filtros futuros por status                        |

---

## Segurança

### Validação de Webhook — HMAC-SHA256

Toda notificação é validada criptograficamente antes de qualquer processamento, seguindo a especificação do Mercado Pago:

1. Extrair `ts` e `v1` do header `x-signature` (formato: `ts=<timestamp>,v1=<hash>`)
2. Construir o manifesto: `id:<dataId_minúsculo>;request-id:<xRequestId>;ts:<ts>;`
3. Calcular HMAC-SHA256 do manifesto com `MERCADOPAGO_WEBHOOK_SECRET` como chave
4. Comparar (case-insensitive) o hash calculado com o `v1` recebido

> **Comportamento fail-secure:** se `MERCADOPAGO_WEBHOOK_SECRET` não estiver configurado, **todos os webhooks são rejeitados com 401** — sem exceção. Isso impede que ambientes mal configurados sejam explorados para fraude.

### Proteção de Dados (LGPD)

- O campo `email` nunca é retornado em endpoints públicos
- `PurchaseStatusResponse` expõe apenas `id`, `status` e `preferenceId`
- Logs nunca contêm o e-mail do comprador
- Mensagens de erro ao cliente são genéricas — detalhes ficam somente nos logs internos

### CORS

| Mapeamento          | Origens                  | Observação                                          |
|---------------------|--------------------------|-----------------------------------------------------|
| `/api/**`           | `APP_CORS_ORIGINS` (env) | Apenas o frontend configurado. `credentials=true`   |
| `/api/webhooks/**`  | `*` (qualquer origem)    | Aberto para o MP. `credentials=false`. Segurança via HMAC |

### Contêiner Docker

- Imagem runtime usa JRE (não JDK) — superfície de ataque reduzida
- Processo roda como usuário não-root (`appuser`, UID 1001)
- Build multi-stage: artefatos de compilação não chegam à imagem final

---

## Configuração e Variáveis de Ambiente

### Perfis Spring

| Perfil   | Arquivo                        | Uso                                                       |
|----------|--------------------------------|-----------------------------------------------------------|
| (base)   | `application.properties`       | Config compartilhada: porta, env vars, Actuator, OpenAPI  |
| `dev`    | `application-dev.properties`   | Dev local. `show-sql=true`, logs `DEBUG`                  |
| `prod`   | `application-prod.properties`  | Produção. `show-sql=false`, logs `INFO`. Flyway ativo     |

### Variáveis de Ambiente

| Variável                      | Descrição                                          | Obrigatório | Exemplo                          |
|-------------------------------|----------------------------------------------------|-------------|----------------------------------|
| `SPRING_PROFILES_ACTIVE`      | Perfil ativo                                       | ✅ Sim      | `prod`                           |
| `DATABASE_URL`                | URL do banco no formato Render (`postgresql://...`)| ✅ Sim*     | `postgresql://user:pass@host/db` |
| `DATABASE_USERNAME`           | Usuário do banco (dev local)                       | ✅ Sim      | `postgres`                       |
| `DATABASE_PASSWORD`           | Senha do banco (dev local)                         | ✅ Sim      | `sua-senha-segura`               |
| `MERCADOPAGO_ACCESS_TOKEN`    | Access token da aplicação no Mercado Pago          | ✅ Sim      | `APP_USR-xxx...`                 |
| `MERCADOPAGO_WEBHOOK_SECRET`  | Segredo HMAC para assinatura dos webhooks          | ✅ Sim      | `abc123def456...`                |
| `MERCADOPAGO_APP_BASE_URL`    | URL pública da API (ativa o envio de webhooks)     | ⚠️ Prod    | `https://app.onrender.com`       |
| `APP_FRONTEND_URL`            | URL do frontend para backUrls e redirects          | ✅ Sim      | `https://app.vercel.app`         |
| `APP_CORS_ORIGINS`            | Origens CORS permitidas (separadas por vírgula)    | ✅ Sim      | `https://app.vercel.app`         |
| `APP_PRODUCT_TITLE`           | Título do produto na preferência do MP             | ❌ Não      | `Curso Completo de Backend`      |
| `APP_PRODUCT_DESCRIPTION`     | Descrição do produto                               | ❌ Não      | `Aprenda a criar APIs...`        |
| `APP_PRODUCT_PRICE`           | Preço em BRL (BigDecimal)                          | ❌ Não      | `99.90`                          |
| `PORT`                        | Porta HTTP (definida automaticamente pelo Render)  | ❌ Não      | `3000`                           |

> `*` `DATABASE_URL` é injetado automaticamente pelo Render. Em dev local, use `DATABASE_USERNAME` e `DATABASE_PASSWORD` separados.

---

## Banco de Dados e Migrations

O schema é gerenciado exclusivamente pelo **Flyway**. O Hibernate está configurado com `ddl-auto=validate` em todos os ambientes — ele apenas verifica a consistência, sem jamais modificar o banco.

> **Por que não `ddl-auto=update`?** O Hibernate pode dropar colunas ou alterar tipos em cenários de renomeação de campos sem aviso. O Flyway garante migrações versionadas, auditáveis e controladas.

### Convenção de nomenclatura

```
src/main/resources/db/migration/V<versão>__<descricao_com_underscores>.sql

# Exemplos:
V1__create_purchases_table.sql
V2__add_email_index.sql
```

### Adicionando uma nova migration

1. Crie o arquivo com o próximo número de versão em `db/migration/`
2. Escreva SQL idempotente (`IF NOT EXISTS`, `IF EXISTS`)
3. Teste localmente com `./mvnw spring-boot:run` — Flyway aplica automaticamente
4. Faça commit e deploy — Flyway aplicará em produção no startup
5. **Nunca modifique** migrations já aplicadas — o Flyway detecta via checksum

---

## Deploy (Render)

O arquivo `render.yaml` define a infraestrutura como código:

- O banco PostgreSQL é provisionado automaticamente pelo Blueprint
- `DATABASE_URL` é injetado no serviço web automaticamente
- `DataSourceConfig` converte o formato `postgresql://` para JDBC na inicialização
- O health check aponta para `/actuator/health`, verificando o banco e o estado real

### Variáveis que devem ser configuradas manualmente no Render Dashboard

```
MERCADOPAGO_ACCESS_TOKEN   → token de produção (painel do Mercado Pago)
MERCADOPAGO_WEBHOOK_SECRET → segredo da assinatura (Integrações > Webhooks)
MERCADOPAGO_APP_BASE_URL   → URL pública do serviço (ex: https://app.onrender.com)
```

### Configurando o Webhook no Mercado Pago

1. Acesse **Painel do MP > Suas integrações > Webhooks**
2. Configure a URL: `https://seu-app.onrender.com/api/webhooks/mercadopago`
3. Marque o evento: **Pagamentos**
4. Copie o **Segredo de assinatura** → defina como `MERCADOPAGO_WEBHOOK_SECRET` no Render
5. Certifique-se de que `MERCADOPAGO_APP_BASE_URL` está definido com a mesma URL base

---

## Testes

| Classe de Teste                    | Tipo           | Cobertura                                                                 |
|------------------------------------|----------------|---------------------------------------------------------------------------|
| `WebhookSignatureValidatorTest`    | Unitário       | 13 casos: secret ausente (fail-secure), assinatura válida/inválida, headers malformados |
| `PurchaseServiceTest`              | Unitário       | Checkout, consulta de status, processamento de webhook, mapeamento de status do MP |
| `CheckoutControllerTest`           | Web (MockMvc)  | HTTP 200/400/502, validação de campos, ausência de email no response, redirects 302 |
| `WebhookControllerTest`            | Web (MockMvc)  | Assinatura inválida (401), tipo não-payment (ignora), `data.id` ausente (400) |
| `PurchaseRepositoryTest`           | Integração     | CRUD real via Testcontainers: save, findById, findByPreferenceId, timestamps |

### Executando os testes

```bash
# Todos os testes (requer Docker para Testcontainers)
./mvnw test

# Apenas unitários e de controller (sem Docker)
./mvnw test -Dtest="**/service/**,**/controller/**"
```

> **Pré-requisito para integração:** Docker deve estar em execução para `PurchaseRepositoryTest` funcionar (Testcontainers sobe um PostgreSQL real).

---

## Desenvolvimento Local

### Pré-requisitos

- Java 17+ (JDK)
- Docker Desktop
- Conta no Mercado Pago com access token de teste

### Configuração inicial

```bash
# 1. Clone e acesse o projeto
git clone <url-do-repositorio>
cd ApiPagamento

# 2. Copie o arquivo de variáveis de ambiente
cp .env.example .env
# Edite o .env e preencha: DATABASE_USERNAME, DATABASE_PASSWORD, MERCADOPAGO_ACCESS_TOKEN

# 3. Suba o PostgreSQL via Docker
docker run -d --name apipagamento-db \
  -e POSTGRES_DB=apipagamento_dev \
  -e POSTGRES_USER=<seu_usuario> \
  -e POSTGRES_PASSWORD=<sua_senha> \
  -p 5432:5432 postgres:15-alpine

# 4. Execute a aplicação (Flyway cria o schema automaticamente)
./mvnw spring-boot:run
```

### Testando localmente

```bash
# Criar um checkout
curl -X POST http://localhost:3000/api/checkout \
  -H "Content-Type: application/json" \
  -d '{"email":"teste@email.com","productId":"CURSO-01"}'

# Consultar status de uma compra
curl http://localhost:3000/api/purchases/1

# Health check
curl http://localhost:3000/actuator/health

# Swagger UI — abra no navegador
open http://localhost:3000/swagger-ui.html
```

### Testando webhooks com ngrok

```bash
# 1. Exponha a porta local
ngrok http 3000

# 2. Copie a URL gerada e adicione ao .env
MERCADOPAGO_APP_BASE_URL=https://abc123.ngrok.io

# 3. Reinicie a aplicação
./mvnw spring-boot:run
```

---

## Tratamento de Erros

Todas as exceções são capturadas pelo `GlobalExceptionHandler` (`@RestControllerAdvice`), que garante respostas padronizadas sem expor detalhes internos ao cliente.

| Exceção Capturada                     | HTTP | Resposta ao Cliente                                          |
|---------------------------------------|------|--------------------------------------------------------------|
| `MethodArgumentNotValidException`     | 400  | Mapa `field → mensagem` de validação no campo `fieldErrors`  |
| `MethodArgumentTypeMismatchException` | 400  | `"Parâmetro inválido: <nome_do_campo>"`                      |
| `RuntimeException`                    | 500  | `"Erro interno. Se o problema persistir, contate o suporte"` |
| `Exception` (fallback)                | 500  | `"Ocorreu um erro inesperado. Tente novamente."`             |

### Formato padrão de erro

```json
// Erro de validação (400)
{
  "timestamp": "2026-03-01T14:30:00.000Z",
  "message": "Dados inválidos na requisição",
  "fieldErrors": {
    "email": "Formato de e-mail inválido"
  }
}

// Erro interno (500) — fieldErrors omitido por @JsonInclude NON_NULL
{
  "timestamp": "2026-03-01T14:30:00.000Z",
  "message": "Erro interno. Se o problema persistir, entre em contato com o suporte."
}
```

---


## Credenciais de Teste — Mercado Pago

Usuário de teste para simular pagamentos em ambiente sandbox do Mercado Pago.

> ⚠️ **Atenção:** estas credenciais são exclusivas para ambiente de testes. Nunca use em produção.

### Dados do Comprador (Buyer Test User)

| Campo                  | Valor                        |
|------------------------|------------------------------|
| **País**               | Brasil                       |
| **User ID**            | 3211823837                   |
| **Usuário de teste**   | TESTUSER3289101407470751788  |
| **Senha**              | zCB6KuXgUX                   |
| **Código de verificação** | 823837                    |

### Cartões de Teste

| Bandeira           | Número               | CVV  | Validade |
|--------------------|----------------------|------|----------|
| Mastercard         | 5031 4332 1540 6351  | 123  | 11/30    |
| Visa               | 4235 6477 2802 5682  | 123  | 11/30    |
| American Express   | 3753 651535 56885    | 1234 | 11/30    |
| Elo Débito         | 5067 7667 8388 8311  | 123  | 11/30    |

### Simulando Resultados de Pagamento

Para forçar um status específico, insira o código desejado no **nome do titular do cartão** durante o checkout:

| Código no titular | Status resultante   | CPF para usar  |
|-------------------|---------------------|----------------|
| `APRO`            | Pagamento aprovado  | 12345678909    |

> Consulte a [documentação oficial do Mercado Pago](https://www.mercadopago.com.br/developers/pt/docs/your-integrations/test/cards) para a lista completa de códigos de simulação (`CONT`, `CALL`, `FUND`, `SECU`, `EXPI`, `FORM` etc.).

### Passo a Passo — Realizar Pagamento de Teste

Siga exatamente esta sequência para que o fluxo funcione corretamente no sandbox:

1. Na tela de checkout do Mercado Pago, insira o e-mail **`test@gmail.com`** no campo solicitado
2. Quando o MP exibir a sugestão de conta, **troque para** o usuário de teste:
   - Usuário: `TESTUSER3289101407470751788`
   - Senha: `zCB6KuXgUX`
3. Clique em **"Outro meio de pagamento"** (não use o meio sugerido automaticamente)
4. Selecione a **bandeira do cartão** desejada (ex: Mastercard)
5. Preencha os dados do cartão conforme a tabela acima
6. No campo **nome do titular**, digite `APRO` para simular pagamento aprovado
7. Insira o **código de verificação** da conta de teste: `823837`
8. Conclua o pagamento

---

> Desenvolvido por Aylan · Spring Boot 4.0.3 · Mercado Pago SDK 2.1.24
