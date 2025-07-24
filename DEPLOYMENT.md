# Deployment Guide – Auth Service

This document explains how to build and deploy the `learning-service` using Docker locally and on a server with [Dokku](https://dokku.com/).

---

## 📦 1. Build Docker Image

First, make sure the `shadowJar` is enabled and your JAR is packaged as a fat JAR.

```bash
./gradlew shadowJar
```

Verify the fat jar exists:

```bash
ls app/build/libs/app-all.jar
```

Then build the Docker image:

```bash
docker build -t learning-service .
```

---

## 🚀 2. Run Locally with Docker

To test locally using the development config:

```bash
docker run -p 3000:3000 -e APP_ENV=dev learning-service
```

Make sure the local database is accessible via the config in `application.dev.json`.

---

## 🌐 3. Deploy to Dokku

### Step 1: Install Dokku Postgres Plugin

On your Dokku server (only once):

```bash
sudo dokku plugin:install https://github.com/dokku/dokku-postgres.git
```

### Step 2: Create App and Database

```bash
dokku apps:create learning-service
dokku postgres:create learning-service-db
dokku postgres:link learning-service-db learning-service
```

This automatically sets the `DATABASE_URL` environment variable in the app.

### Step 3: Configure App Environment

Set environment variable for production mode:

```bash
dokku config:set learning-service APP_ENV=prod
```

### Step 4: Enable Port and Healthcheck (Optional)

Create an `app.json` file in your project root:

```json
{
  "healthchecks": {
    "web": [
      {
        "type": "startup",
        "name": "web check",
        "description": "Check if service is up",
        "path": "/health",
        "attempts": 3
      }
    ]
  }
}
```

---

## 🧪 4. Deploy via Git Push

Ensure your remote is set up:

```bash
git remote add dokku dokku@your-server-ip:learning-service
```

Then push:

```bash
git push dokku main
```

Dokku will build the Docker image using your `Dockerfile` and run the container.

---

## 📍 Troubleshooting

- `UnsupportedClassVersionError`: Make sure your Docker base image (`openjdk:17`) matches your JDK compile target.
- `no main manifest attribute`: You’re not using the fat JAR (`app-all.jar`), or forgot to define `mainClass` in the Gradle config.
- Healthcheck fails: Ensure the `/health` endpoint is live and working under `APP_ENV=prod`.

---

## ✅ Done

Your auth service should now be deployed and accessible on:

```
http://your-domain-or-ip
```

Check logs with:

```bash
dokku logs learning-service
```
