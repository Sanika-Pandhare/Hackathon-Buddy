<<<<<<< HEAD
# Hackathon Buddy

AI-Powered Hackathon Team Formation & Collaboration Platform.

## System Architecture

The system consists of three main components:
1. **Frontend**: React.js + Vite
2. **Backend**: Spring Boot + MySQL
3. **AI Service**: Python FastAPI + Scikit-learn

## Setup Instructions

### Prerequisites
- Node.js (v18+)
- Java 17
- Maven
- Python 3.10+
- MySQL 8.0+
- Docker & Docker Compose (optional, for easy setup)

### Environment Variables
Copy the `.env.example` file to `.env` in the root directory:
```bash
cp .env.example .env
```
Update the `.env` file with your actual database credentials, JWT secrets, and API keys.

### Running with Docker Compose (Recommended)
You can start all services (MySQL, Backend, Frontend, AI Service) using Docker Compose:
```bash
docker-compose up -d --build
```

### Running Locally (Without Docker)

#### 1. Database
Ensure MySQL is running and create a database named `hackathon_buddy`. Update credentials in `backend/src/main/resources/application.yml` or use the `.env` file.

#### 2. Backend (Spring Boot)
```bash
cd backend
./mvnw spring-boot:run
# The API will be available at http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html
```

#### 3. AI Service (Python FastAPI)
```bash
cd ai-service
python -m venv venv
# Activate venv: `venv\Scripts\activate` on Windows or `source venv/bin/activate` on Linux/Mac
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
# The AI API will be available at http://localhost:8000
# Docs: http://localhost:8000/docs
```

#### 4. Frontend (React + Vite)
```bash
cd frontend
npm install
npm run dev
# The Frontend will be available at http://localhost:5173
```

## Documentation
- [Architecture](docs/architecture.md)
- [Database Schema](database/schema.sql)
=======
# React + Vite

This template provides a minimal setup to get React working in Vite with HMR and some Oxlint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the Oxlint configuration

If you are developing a production application, we recommend using TypeScript with type-aware lint rules enabled. Check out the [TS template](https://github.com/vitejs/vite/tree/main/packages/create-vite/template-react-ts) for information on how to integrate TypeScript and Oxlint's TypeScript related rules in your project.
>>>>>>> 5e2b7a9d34cd08c2e3d078055ade2e92ef7e4c61
