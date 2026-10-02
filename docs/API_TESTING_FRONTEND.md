# API testing and frontend integration

## Base URL

The backend runs locally on `http://localhost:8080`.

The API is available under both old and front-friendly paths:

- `/auth`, `/admin`, `/customers`, `/charges`, `/webhooks/mercadopago`
- `/api/auth`, `/api/admin`, `/api/customers`, `/api/charges`, `/api/webhooks/mercadopago`

For a front-end, prefer `/api` prefix.

## Quick smoke tests

### 1) Login

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@imperio-dog.local",
    "password": "TroqueEstaSenha123!"
  }'
```

Expected result: HTTP 200 with JSON containing `token`, `type`, and `role`.

### 2) List customers (admin)

```bash
curl -X GET http://localhost:8080/api/admin/customers \
  -H "Authorization: Bearer <TOKEN>"
```

### 3) Get client profile

```bash
curl -X GET http://localhost:8080/api/customers/me \
  -H "Authorization: Bearer <CLIENT_TOKEN>"
```

### 4) List charges for current customer

```bash
curl -X GET http://localhost:8080/api/charges/my-charges \
  -H "Authorization: Bearer <CLIENT_TOKEN>"
```

## Frontend usage example

### Axios

```js
const api = axios.create({
  baseURL: 'http://localhost:8080/api',
  headers: { 'Content-Type': 'application/json' }
});

const login = async () => {
  const response = await api.post('/auth/login', {
    email: 'admin@imperio-dog.local',
    password: 'TroqueEstaSenha123!'
  });

  const token = response.data.token;
  api.defaults.headers.common.Authorization = `Bearer ${token}`;
  return token;
};

const getCustomers = async () => {
  const { data } = await api.get('/admin/customers');
  return data;
};
```

### Fetch with token store

```js
const token = localStorage.getItem('token');

fetch('http://localhost:8080/api/customers/me', {
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  }
})
  .then(res => res.json())
  .then(console.log);
```

## Request/response contract

Request contracts are strictly separated from response contracts.

Examples:

- `PetRequestDTO` for create/update payloads
- `PetResponseDTO` for output payloads
- `ServiceRequestDTO` for create/update payloads
- `ServiceResponseDTO` for output payloads

Examples of body payloads:

```json
{
  "name": "Rex",
  "species": "Cachorro",
  "breed": "Vira-lata",
  "customerId": 1
}
```

```json
{
  "id": 10,
  "name": "Rex",
  "species": "Cachorro",
  "breed": "Vira-lata",
  "customerId": 1
}
```

## Error handling

Errors are returned as JSON with `status` and `error`, and validation errors include `fields` when applicable.

Example:

```json
{
  "status": 400,
  "error": "Validation failed",
  "fields": {
    "email": "must be a well-formed email address"
  }
}
```

## Testing with Maven

Run the full suite from the project root:

```powershell
$env:JAVA_HOME = 'C:\Users\54966283850\.jdks\temurin-21.0.12.1'
.\mvnw test -q
```

This validates the Spring context and the unit tests for services.
