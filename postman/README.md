# Learning Service - Colección de Postman

Esta colección de Postman contiene todos los endpoints disponibles del Learning Service, actualizados con la nueva estructura de rutas RESTful.

## 📁 Archivos incluidos

- `Learning Service.postman_collection.json` - Colección principal con todos los endpoints
- `Learning Service-Local.postman_environment.json` - Variables de entorno para desarrollo local
- `Learning Service-Production.postman_environment.json` - Variables de entorno para producción

## 🚀 Endpoints actualizados

### **Dashboard Endpoints**

#### 1. **Get Level Overview**
```
GET /levels/{language}
```
- **Descripción**: Obtiene un resumen de todos los niveles disponibles para un idioma
- **Ejemplo**: `GET /levels/en`
- **Variables**: `{{target_language}}` (ej: "en", "es")

#### 2. **Get Level Topics**
```
GET /levels/{language}/{level}/topics
```
- **Descripción**: Obtiene todos los temas de un nivel específico con progreso detallado
- **Ejemplo**: `GET /levels/en/A1/topics`
- **Variables**: `{{target_language}}`, `{{level}}`

#### 3. **Get Specific Exercise**

```
GET /levels/{language}/{level}/topics/{topicId}/exercises/{exerciseId}
```

- **Descripción**: Obtiene un ejercicio específico por su ID
- **Ejemplo**: `GET /levels/en/A1/topics/articles_1/exercises/ex_1`
- **Variables**: `{{target_language}}`, `{{level}}`, `{{topic_id}}`, `{{exercise_id}}`
- **Nota**: Incluye la solución solo si el ejercicio ya fue completado

#### 4. **Get Next Exercise (Practice Mode)**

```
GET /levels/{language}/{level}/topics/{topicId}/exercises/next
```

- **Descripción**: Obtiene el siguiente ejercicio que el usuario debe completar en un tema
- **Ejemplo**: `GET /levels/en/A1/topics/articles_1/exercises/next`
- **Variables**: `{{target_language}}`, `{{level}}`, `{{topic_id}}`
- **Lógica**: Devuelve el ejercicio basado en el progreso del usuario (si completó 2, devuelve el 3°)

## 🔧 Variables de colección

| Variable | Valor por defecto | Descripción |
|----------|------------------|-------------|
| `base_url` | `http://localhost:3001` | URL base del servicio |
| `jwt_token` | `eyJ0eXAiOiJKV1Q...` | Token JWT para autenticación |
| `target_language` | `en` | Idioma objetivo (en, es) |
| `level` | `A1` | Nivel de aprendizaje |
| `topic_id` | `articles_1` | ID del tema |
| `exercise_id` | `ex_1` | ID del ejercicio |
| `user_id` | `testuser123` | ID del usuario para pruebas |

## 📝 Cambios principales

### **Estructura de URLs actualizada**
- ❌ **Antes**: `/levels?targetLanguage=en`
- ✅ **Ahora**: `/levels/en`

- ❌ **Antes**: `/levels/A1/topics?targetLanguage=en`
- ✅ **Ahora**: `/levels/en/A1/topics`

### **Nuevos campos en las respuestas**

#### **TopicSummary** (mejorado):
```json
{
  "id": "articles_1",
  "title": {
    "en": "Articles",
    "es": "Artículos"
  },
  "description": {
    "en": "Learn to use definite and indefinite articles in English.",
    "es": "Aprende a usar los artículos definidos e indefinidos en inglés."
  },
  "status": "completed",
  "progress": {
    "completedExercises": 4,
    "correctAnswers": 4,
    "wrongAnswers": 0,
    "totalExercises": 4,
    "lastAttempted": "2024-01-15T10:30:00Z"
  }
}
```

#### **ExerciseResponse** (nuevo):
```json
{
  "id": "ex_4",
  "topicId": "articles_1",
  "type": "translation",
  "prompt": {
    "es": "El perro está corriendo"
  },
  "solution": null,
  "options": null,
  "previousAttempts": 0,
  "isCompleted": false
}
```

### **Prompts multiidioma**
Los ejercicios ahora soportan prompts en múltiples idiomas:
- **String simple**: `"___ book is on the table."`
- **Multiidioma**: `{"es": "El perro está corriendo"}`

## 🔐 Autenticación

Todos los endpoints requieren autenticación JWT:
```
Authorization: Bearer {{jwt_token}}
```

El token incluido en la colección es válido hasta enero 2025 para pruebas.

## 🧪 Ejemplos de uso

### **1. Flujo completo de práctica**
1. `GET /levels/en` - Ver niveles disponibles
2. `GET /levels/en/A1/topics` - Ver temas del nivel A1
3. `GET /levels/en/A1/topics/articles_1/exercises/next` - Obtener el siguiente ejercicio del tema "articles_1"
4. Completar el ejercicio (endpoint de respuesta no incluido aún)
5. Repetir paso 3 para el siguiente ejercicio

### **2. Revisar ejercicio específico**
```
GET /levels/en/A1/topics/articles_1/exercises/ex_3
```

### **3. Cambiar idioma objetivo**
Cambiar `{{target_language}}` a "es" para practicar español:
```
GET /levels/es/A1/topics
```

## 📊 Tipos de ejercicios soportados

1. **fill-in-the-blank**: Llenar espacios en blanco
2. **multiple-choice**: Selección múltiple  
3. **translation**: Traducción (con prompt multiidioma)

## 🔄 Estados del progreso

- **available**: Disponible para completar
- **in_progress**: Comenzado pero no terminado
- **completed**: Completado exitosamente
- **locked**: Bloqueado (completar tema anterior primero)
