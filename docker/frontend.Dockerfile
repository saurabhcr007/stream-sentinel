# Build stage
FROM node:20-alpine AS build

WORKDIR /app

# Copy package files and install dependencies
COPY frontend/package*.json ./
RUN npm ci

# Copy source code and build the React app
COPY frontend/ ./
RUN npm run build

# Production serve stage using Nginx
FROM nginx:alpine

# Copy built assets to Nginx
COPY --from=build /app/dist /usr/share/nginx/html

# Copy a basic nginx configuration to handle React Router if needed
# We are just exposing port 80 based on default Nginx behavior
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
