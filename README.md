# Imperio Dog API

API REST para gestão de clientes, pets e serviços de um pet shop, com autenticação JWT e cobrança por boleto via Mercado Pago. O código usa Java 21, Spring Boot, Spring Security, Spring Data JPA e MySQL.

## Histórico de implementação

O projeto foi organizado em commits por etapa funcional:

1. `chore: bootstrap Spring Boot API project` — estrutura Maven e aplicação inicial.
2. `feat: add API application entry point and configuration` — aplicação, configuração e teste de contexto.
3. `feat: model users and access roles` — entidade de usuário e papéis.
4. `feat: model customers and pets` — clientes e pets.
5. `feat: model services and customer charges` — serviços, cobranças e estados.
6. `feat: add Spring Data repositories` — persistência e consultas derivadas.
7. `feat: define API DTOs and customer billing address` — contratos HTTP e endereço necessário para boleto.
8. `feat: secure API with stateless JWT authentication` — login, token e autorização por papel.
9. `feat: implement customer pet and service workflows` — regras de cadastro e CRUD.
10. `feat: integrate boleto charges and payment webhooks` — Mercado Pago, cobranças e notificações.
11. `feat: expose secured REST API endpoints` — controllers REST.

As camadas ficam sob `com.example.imperiodogapi`: `dto`, `controller`, `service`, `repository`, `security`, `config` e `entities`. Os DTOs mantêm os payloads HTTP separados das entidades JPA.

## Requisitos

- JDK 21.
- MySQL disponível localmente ou em um servidor acessível.
- Credenciais de acesso do Mercado Pago e uma chave secreta de Webhooks.
- Para receber notificações em desenvolvimento local, uma URL HTTPS pública encaminhada para a aplicação, por exemplo por um túnel HTTPS.

## Configuração

O `application.properties` aceita estas variáveis de ambiente:

| Variável | Uso |
| --- | --- |
| `DB_URL` | JDBC do MySQL; padrão: `jdbc:mysql://localhost:3306/imperio_dog?...` |
| `DB_USERNAME` | Usuário do MySQL; padrão: `root` |
| `DB_PASSWORD` | Senha do MySQL |
| `JWT_SECRET` | Chave HMAC do JWT, com pelo menos 32 bytes UTF-8 |
| `JWT_EXPIRATION_MS` | Validade do token em milissegundos; padrão: 24 horas |
| `MERCADOPAGO_ACCESS_TOKEN` | Access token do Mercado Pago |
| `MERCADOPAGO_WEBHOOK_SECRET` | Segredo de assinatura configurado no painel do Mercado Pago |
| `MERCADOPAGO_NOTIFICATION_URL` | URL pública de `POST /webhooks/mercadopago` |

Exemplo no PowerShell (substitua os valores):

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3306/imperio_dog?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
$env:DB_USERNAME = 'root'
$env:DB_PASSWORD = 'senha-local'
$env:JWT_SECRET = 'troque-por-uma-chave-aleatoria-com-no-minimo-32-bytes'
$env:JWT_EXPIRATION_MS = '86400000'
$env:MERCADOPAGO_ACCESS_TOKEN = 'APP_USR-seu-access-token'
$env:MERCADOPAGO_WEBHOOK_SECRET = 'seu-segredo-de-webhook'
$env:MERCADOPAGO_NOTIFICATION_URL = 'https://seu-dominio-publico/webhooks/mercadopago'
```

O programa falha ao iniciar se faltarem `JWT_SECRET`, `MERCADOPAGO_ACCESS_TOKEN` ou `MERCADOPAGO_WEBHOOK_SECRET`. Use credenciais de teste do Mercado Pago durante o desenvolvimento. O endereço do cliente é solicitado no cadastro e enviado ao provedor porque o boleto precisa dos dados do pagador.

## Iniciar e compilar

Na raiz do projeto:

```powershell
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd spring-boot:run
```

Com o MySQL disponível e as variáveis configuradas, execute o teste de contexto existente com:

```powershell
.\mvnw.cmd test
```

O teste de contexto sobe a aplicação e, portanto, precisa de banco e das variáveis obrigatórias. Os exemplos abaixo são testes manuais de API; não dependem de novos testes automatizados.

## Criar o primeiro administrador

Não existe cadastro público de administradores. Inicialize a aplicação uma vez para o Hibernate criar as tabelas e então crie o primeiro administrador diretamente no banco. Gere o hash BCrypt da senha escolhida:

```powershell
.\mvnw.cmd dependency:build-classpath '-Dmdep.outputFile=target/classpath.txt'
$classpath = 'target/classes;' + (Get-Content target/classpath.txt -Raw).Trim()
@'
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
System.out.println(new BCryptPasswordEncoder(12).encode("TroqueEstaSenha123!"));
/exit
'@ | jshell --class-path $classpath
```

Insira o hash impresso no campo `password` (não insira a senha em texto puro). Ajuste email e telefone:

```sql
INSERT INTO users
  (name, email, password, phone, cpf, role)
VALUES
  ('Administrador', 'admin@imperio-dog.local', '$2a$12$COLE_AQUI_O_HASH_BCRYPT', NULL, '52998224725', 'ADMIN');
```

O CPF deve ser único na base. Não exponha credenciais reais em scripts versionados.

## Fluxo de teste da API

Os comandos seguintes usam `Invoke-RestMethod` do PowerShell, com a aplicação em `http://localhost:8080`. Primeiro autentique o administrador; as rotas `/admin/**` exigem o papel `ROLE_ADMIN`.

```powershell
$base = 'http://localhost:8080'
$adminLogin = Invoke-RestMethod -Method Post -Uri "$base/auth/login" `
  -ContentType 'application/json' `
  -Body (@{ email = 'admin@imperio-dog.local'; password = 'TroqueEstaSenha123!' } | ConvertTo-Json)
$adminHeaders = @{ Authorization = "Bearer $($adminLogin.token)" }
```

Crie um cliente com CPF válido e endereço de cobrança, depois crie um serviço e um pet vinculado ao cliente:

```powershell
$customerBody = @{
  name = 'Maria da Silva'
  email = 'maria@example.com'
  password = 'SenhaForte123!'
  phone = '11999999999'
  cpf = '529.982.247-25'
  zipCode = '01310-100'
  streetName = 'Avenida Paulista'
  streetNumber = '1000'
  neighborhood = 'Bela Vista'
  city = 'São Paulo'
  federalUnit = 'SP'
} | ConvertTo-Json
$customer = Invoke-RestMethod -Method Post -Uri "$base/admin/customers" `
  -Headers $adminHeaders -ContentType 'application/json' -Body $customerBody

$service = Invoke-RestMethod -Method Post -Uri "$base/admin/services" `
  -Headers $adminHeaders -ContentType 'application/json' `
  -Body (@{ name = 'Banho'; description = 'Banho completo'; price = 80.00 } | ConvertTo-Json)

$pet = Invoke-RestMethod -Method Post -Uri "$base/admin/pets" `
  -Headers $adminHeaders -ContentType 'application/json' `
  -Body (@{ name = 'Bidu'; species = 'Cachorro'; breed = 'SRD'; customerId = $customer.id } | ConvertTo-Json)
```

Crie uma cobrança. O vencimento do boleto precisa estar pelo menos três dias no futuro. A chamada usa o serviço de teste do Mercado Pago e retorna o link do boleto:

```powershell
$serviceDate = (Get-Date).ToString('yyyy-MM-dd')
$dueDate = (Get-Date).AddDays(4).ToString('yyyy-MM-dd')
$charge = Invoke-RestMethod -Method Post -Uri "$base/admin/charges" `
  -Headers $adminHeaders -ContentType 'application/json' `
  -Body (@{
    customerId = $customer.id
    petId = $pet.id
    serviceId = $service.id
    serviceDate = $serviceDate
    dueDate = $dueDate
  } | ConvertTo-Json)
$charge | Format-List
```

O Mercado Pago pode exigir usuários pagadores de teste compatíveis com a conta de teste usada. Use também email, CPF e dados de endereço aceitos pelo ambiente de teste dessa conta.

Autentique o cliente para conferir `/customers/me` e `/charges/my-charges`:

```powershell
$customerLogin = Invoke-RestMethod -Method Post -Uri "$base/auth/login" `
  -ContentType 'application/json' `
  -Body (@{ email = 'maria@example.com'; password = 'SenhaForte123!' } | ConvertTo-Json)
$customerHeaders = @{ Authorization = "Bearer $($customerLogin.token)" }
Invoke-RestMethod -Method Get -Uri "$base/customers/me" -Headers $customerHeaders
Invoke-RestMethod -Method Get -Uri "$base/charges/my-charges" -Headers $customerHeaders
Invoke-RestMethod -Method Get -Uri "$base/charges/$($charge.id)" -Headers $customerHeaders
```

Confira também a listagem administrativa e o filtro por status:

```powershell
Invoke-RestMethod -Method Get -Uri "$base/admin/customers" -Headers $adminHeaders
Invoke-RestMethod -Method Get -Uri "$base/admin/charges?status=PENDING" -Headers $adminHeaders
```

Teste autorização: uma chamada sem token a `/customers/me` deve responder `401`; uma chamada com token de cliente a `/admin/customers` deve responder `403`. Um cliente também deve receber `403` ao consultar a cobrança pertencente a outra pessoa.

## Testar webhook de pagamento

Configure `MERCADOPAGO_NOTIFICATION_URL` para uma URL HTTPS pública que encaminhe para a aplicação e cadastre essa URL nas notificações da aplicação no painel do Mercado Pago. O controller lê `data.id` da query string e valida `x-signature` com `x-request-id` antes de enfileirar o processamento. A aplicação consulta o status real do pagamento no Mercado Pago; o corpo enviado pelo webhook não é usado como fonte confiável do status.

Para exercitar a verificação HTTP com uma cobrança existente, gere uma assinatura atual usando o segredo configurado. O processamento subsequente só encontrará a cobrança se `$paymentId` for o ID real do pagamento correspondente:

```powershell
$paymentId = 'ID_REAL_DO_PAGAMENTO_MERCADO_PAGO'
$requestId = [guid]::NewGuid().ToString()
$timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds().ToString()
$manifest = "id:$paymentId;request-id:$requestId;ts:$timestamp;"
$hmac = [System.Security.Cryptography.HMACSHA256]::new(
  [System.Text.Encoding]::UTF8.GetBytes($env:MERCADOPAGO_WEBHOOK_SECRET))
$signature = [Convert]::ToHexString(
  $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($manifest))).ToLowerInvariant()
$webhookHeaders = @{
  'x-request-id' = $requestId
  'x-signature' = "ts=$timestamp,v1=$signature"
}
Invoke-RestMethod -Method Post -Uri "$base/webhooks/mercadopago?data.id=$paymentId" `
  -Headers $webhookHeaders
```

A rota responde `200` depois de aceitar a notificação para processamento assíncrono. Para validar o fluxo ponta a ponta, gere um pagamento de teste, configure o webhook real no painel e confira novamente `GET /charges/my-charges`; os estados reconhecidos são `approved` → `PAID`, `pending`/`in_process` → `PAYMENT_IN_PROCESS` e `cancelled`/`rejected` → `CANCELED`.

## Rotas

| Método e rota | Acesso | Ação |
| --- | --- | --- |
| `POST /auth/login` | Público | Autentica e retorna JWT |
| `POST /webhooks/mercadopago?data.id=...` | Público com HMAC válido | Recebe notificação de pagamento |
| `POST /admin/customers`, `GET /admin/customers` | `ROLE_ADMIN` | Cria e lista clientes |
| `POST /admin/services`, `GET /admin/services` | `ROLE_ADMIN` | Cria e lista serviços |
| `GET/PUT/DELETE /admin/services/{id}` | `ROLE_ADMIN` | Consulta, atualiza ou remove serviço |
| `POST /admin/pets`, `GET /admin/pets` | `ROLE_ADMIN` | Cria e lista pets |
| `GET/PUT/DELETE /admin/pets/{id}` | `ROLE_ADMIN` | Consulta, atualiza ou remove pet |
| `POST /admin/charges`, `GET /admin/charges?status=...` | `ROLE_ADMIN` | Cria boleto e lista cobranças |
| `GET /customers/me` | `ROLE_CLIENT` | Consulta o próprio perfil |
| `GET /charges/my-charges` | `ROLE_CLIENT` | Lista cobranças pelo email do JWT |
| `GET /charges/{id}` | Autenticado; dono ou admin | Consulta cobrança com verificação de propriedade |

## Limitações operacionais

- A primeira conta `ADMIN` é provisionada fora da API, por SQL ou migração controlada.
- O processo de criação chama o Mercado Pago e requer credenciais válidas e conectividade externa.
- `spring.jpa.hibernate.ddl-auto=update` é adequado ao desenvolvimento inicial; em produção, use migrações versionadas e revise os parâmetros de banco e logs.
- O teste automatizado atual é apenas de contexto Spring. O roteiro acima cobre manualmente os principais fluxos HTTP e depende de uma conta de teste do Mercado Pago para criar boletos reais de teste.
