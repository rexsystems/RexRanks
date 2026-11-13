FROM node:22-alpine
WORKDIR /app
COPY package.json package-lock.json* ./
# Frontend is already built and copied in as /app/dist by the build context.
COPY dist ./dist
COPY server ./server
ENV NODE_ENV=production
ENV PORT=7781
ENV DATA_DIR=/data
ENV DIST_DIR=/app/dist
VOLUME ["/data"]
EXPOSE 7781
USER node
CMD ["node", "server/index.mjs"]
