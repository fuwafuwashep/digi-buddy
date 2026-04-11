import 'dotenv/config';
import { createServer } from 'http';
import app from './app';
import { initSocket } from './socket';
import { logger } from './utils/logger';

const PORT = process.env.PORT || 3000;

const httpServer = createServer(app);
initSocket(httpServer);

httpServer.listen(PORT, () => {
  logger.info(`🚀 DigiBuddy server running on port ${PORT}`);
  logger.info(`📡 Mode: ${process.env.NODE_ENV || 'development'}`);
  logger.info(`🌐 API: http://localhost:${PORT}/api`);
});

process.on('unhandledRejection', (err: Error) => { logger.error('Unhandled rejection:', err); process.exit(1); });
