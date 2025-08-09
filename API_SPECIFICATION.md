# Learning Service API Specification

## Información General

- **Base URL**: `http://localhost:3001` (desarrollo)
- **Autenticación**: JWT Bearer Token (excepto para endpoints de gestión de usuarios y health)
- **Content-Type**: `application/json`
- **Respuestas de Error**: Siguiendo formato estándar con `error`, `message`, `timestamp`, `path`

## Autenticación

La mayoría de los endpoints requieren autenticación JWT. Incluir el token en el header:
```
Authorization: Bearer <jwt_token>
```

El JWT debe contener el claim `userId` con el ID del usuario.

---

## Endpoints

### 1. Health Check

#### GET /health
Verificar el estado del servicio.

**Autenticación**: No requerida

**Response**:
```json
{
  "status": "UP",
  "service": "learning-service"
}
```

---

### 2. Gestión de Usuarios

#### POST /users
Crear un nuevo usuario (usado por el servicio de autenticación).

**Autenticación**: Header `X-Internal-Secret` requerido

**Request Body**:
```json
{
  "userId": "uuid-string"
}
```

**Responses**:
- `201 Created`: Usuario creado exitosamente
- `409 Conflict`: Usuario ya existe
- `400 Bad Request`: Datos inválidos

```json
{
  "success": true,
  "message": "User created successfully",
  "userId": "uuid-string"
}
```

#### DELETE /users/{userId}
Eliminar un usuario y todos sus datos.

**Autenticación**: Header `X-Internal-Secret` requerido

**Response**:
```json
{
  "success": true,
  "message": "User deleted successfully",
  "userId": "uuid-string"
}
```

---

### 3. Configuración de Usuario

#### GET /users/settings
Obtener la configuración del usuario.

**Autenticación**: JWT requerido

**Response**:
```json
{
  "userId": "uuid-string",
  "nativeLanguage": "en",
  "targetLanguage": "es",
  "darkMode": false,
  "onboardingStep": "complete",
  "userLevel": "A1"
}
```

#### PUT /users/settings
Actualizar la configuración del usuario.

**Autenticación**: JWT requerido

**Request Body**:
```json
{
  "nativeLanguage": "en",
  "targetLanguage": "es",
  "darkMode": true,
  "onboardingStep": "complete",
  "userLevel": "A2"
}
```

**Response**:
```json
{
  "settings": {
    "userId": "uuid-string",
    "nativeLanguage": "en",
    "targetLanguage": "es",
    "darkMode": true,
    "onboardingStep": "complete",
    "userLevel": "A2"
  },
  "warnings": [
    "Changing target language will reset your progress"
  ]
}
```

**Notas**:
- Todos los campos son opcionales
- `onboardingStep`: `"native"`, `"learning"`, `"level"`, `"complete"`
- `userLevel`: `"A1"`, `"A2"`, `"B1"`, `"B2"`, `"C1"`, `"C2"`
- El sistema previene automáticamente conflictos entre idiomas nativo y objetivo

#### DELETE /users/settings/progress
Resetear el progreso del usuario.

**Autenticación**: JWT requerido

**Response**:
```json
{
  "success": true,
  "message": "Progress reset successfully"
}
```

---

### 4. Contenido Educativo

#### GET /content/{lang}/modules
Obtener todos los módulos para un idioma.

**Autenticación**: JWT requerido

**Parámetros**:
- `lang`: Código de idioma (`en`, `es`)

**Response**:
```json
[
  {
    "id": "articles-determiners",
    "title": {
      "en": "Articles and Determiners",
      "es": "Artículos y Determinantes"
    },
    "description": {
      "en": "Learn about definite and indefinite articles",
      "es": "Aprende sobre artículos definidos e indefinidos"
    },
    "level": "A1",
    "totalUnits": 5,
    "completedUnits": 2,
    "status": "in_progress"
  }
]
```

#### GET /content/{lang}/modules/{moduleId}
Obtener detalles de un módulo específico.

**Autenticación**: JWT requerido

**Response**:
```json
{
  "id": "articles-determiners",
  "title": {
    "en": "Articles and Determiners",
    "es": "Artículos y Determinantes"
  },
  "description": {
    "en": "Learn about definite and indefinite articles",
    "es": "Aprende sobre artículos definidos e indefinidos"
  },
  "level": "A1",
  "units": [
    {
      "id": "the_article_general_vs_specific_1",
      "title": {
        "en": "The Article: General vs Specific",
        "es": "El Artículo: General vs Específico"
      },
      "description": {
        "en": "Learn when to use 'the' with general vs specific nouns",
        "es": "Aprende cuándo usar 'the' con sustantivos generales vs específicos"
      },
      "totalExercises": 50,
      "completedExercises": 20,
      "status": "in_progress"
    }
  ]
}
```

#### GET /content/{lang}/modules/{moduleId}/units
Obtener todas las unidades de un módulo.

**Autenticación**: JWT requerido

**Response**: Array de objetos `UnitSummary` (mismo formato que en el detalle del módulo)

#### GET /content/{lang}/modules/{moduleId}/units/{unitId}
Obtener detalles de una unidad específica.

**Autenticación**: JWT requerido

**Response**:
```json
{
  "id": "the_article_general_vs_specific_1",
  "title": {
    "en": "The Article: General vs Specific",
    "es": "El Artículo: General vs Específico"
  },
  "description": {
    "en": "Learn when to use 'the' with general vs specific nouns",
    "es": "Aprende cuándo usar 'the' con sustantivos generales vs específicos"
  },
  "tip": {
    "en": "Remember: use 'the' for specific things, omit for general concepts",
    "es": "Recuerda: usa 'the' para cosas específicas, omite para conceptos generales"
  },
  "exercises": [
    {
      "id": "ex_1",
      "type": "translation",
      "status": "completed",
      "isCorrect": true
    },
    {
      "id": "ex_2", 
      "type": "fill-in-the-blank",
      "status": "available",
      "isCorrect": null
    }
  ]
}
```

#### GET /content/{lang}/modules/{moduleId}/units/{unitId}/exercises
Obtener todos los ejercicios de una unidad.

**Autenticación**: JWT requerido

**Response**: Array de objetos `ExerciseSummary`

#### GET /content/{lang}/modules/{moduleId}/units/{unitId}/exercises/{exerciseId}
Obtener detalles de un ejercicio específico.

**Autenticación**: JWT requerido

**Response**:
```json
{
  "id": "ex_1",
  "type": "translation",
  "prompt": {
    "en": "Translate to Spanish: The cat is sleeping",
    "es": "Traduce al inglés: El gato está durmiendo"
  },
  "solution": "El gato está durmiendo",
  "options": null,
  "tip": {
    "en": "Remember that 'the' translates to 'el' for masculine nouns",
    "es": "Recuerda que 'the' se traduce como 'el' para sustantivos masculinos"
  }
}
```

#### POST /content/{lang}/modules/{moduleId}/units/{unitId}/exercises/{exerciseId}/submit
Enviar respuesta de un ejercicio.

**Autenticación**: JWT requerido

**Request Body**:
```json
{
  "userAnswer": "El gato está durmiendo",
  "answerStatus": "CORRECT"
}
```

**AnswerStatus**: `"CORRECT"`, `"INCORRECT"`, `"SKIPPED"`, `"REVEALED"`

**Response**:
```json
{
  "success": true,
  "answerStatus": "CORRECT",
  "correctAnswer": "El gato está durmiendo",
  "explanation": {
    "en": "Great! You correctly used 'el' for the masculine noun 'gato'",
    "es": "¡Genial! Usaste correctamente 'el' para el sustantivo masculino 'gato'"
  },
  "progress": {
    "unitId": "the_article_general_vs_specific_1",
    "completedExercises": 21,
    "correctAnswers": 20,
    "wrongAnswers": 1,
    "totalExercises": 50,
    "lastAttempted": "2025-08-09T16:34:37.000Z"
  }
}
```

---

### 5. Progreso del Usuario

#### POST /progress/submit
Enviar progreso de ejercicio (endpoint alternativo/legacy).

**Autenticación**: JWT requerido

**Request Body**:
```json
{
  "targetLang": "es",
  "moduleId": "articles-determiners",
  "unitId": "the_article_general_vs_specific_1",
  "exerciseId": "ex_1",
  "userAnswer": "El gato está durmiendo",
  "answerStatus": "CORRECT"
}
```

**Response**: Mismo formato que el endpoint de submit en content

---

## Modelos de Datos

### UnitProgress
```json
{
  "unitId": "string",
  "completedExercises": 0,
  "correctAnswers": 0,
  "wrongAnswers": 0,
  "totalExercises": 0,
  "lastAttempted": "2025-08-09T16:34:37.000Z"
}
```

### Exercise Types
- `translation`: Ejercicios de traducción
- `fill-in-the-blank`: Llenar espacios en blanco
- `multiple-choice`: Opción múltiple

### Status Values
- `available`: Disponible para hacer
- `in_progress`: En progreso
- `completed`: Completado
- `locked`: Bloqueado (prerrequisitos no cumplidos)

---

## Códigos de Error

### Autenticación
- `401 Unauthorized`: Token inválido o faltante
- `403 Forbidden`: Permisos insuficientes

### Validación
- `400 Bad Request`: Datos de entrada inválidos
- `422 Unprocessable Entity`: Datos válidos pero lógicamente incorrectos

### Recursos
- `404 Not Found`: Recurso no encontrado
- `409 Conflict`: Conflicto (ej: usuario ya existe)

### Servidor
- `500 Internal Server Error`: Error interno del servidor

### Formato de Error
```json
{
  "error": "Bad Request",
  "message": "Language and module ID are required",
  "timestamp": "2025-08-09T16:34:37.000Z",
  "path": "/content/es/modules"
}
```

---

## Notas de Implementación

1. **Idiomas Soportados**: Actualmente `en` (inglés) y `es` (español)

2. **Estructura Jerárquica**: Module → Unit → Exercise

3. **Progreso**: Se calcula dinámicamente basado en resultados individuales de ejercicios

4. **Configuración de Usuario**: 
   - El sistema previene automáticamente que el idioma nativo y objetivo sean iguales
   - Cambiar el idioma objetivo reinicia el progreso
   - El onboarding debe completarse secuencialmente

5. **Contenido**: Se sirve desde archivos JSON estáticos en el ContentLibrary

6. **Autenticación Interna**: Los endpoints de gestión de usuarios usan `X-Internal-Secret` para comunicación entre servicios
