import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import morgan from 'morgan';
import rateLimit from 'express-rate-limit';
import path from 'path';

import authRoutes    from './routes/auth';
import helperRoutes  from './routes/helpers';
import bookingRoutes from './routes/bookings';
import ratingRoutes  from './routes/ratings';
import chatRoutes    from './routes/chat';
import userRoutes    from './routes/users';

const app = express();

app.use(helmet());
app.use(cors({
  origin: (process.env.ALLOWED_ORIGINS || 'http://localhost:3000').split(','),
  credentials: true,
}));

const globalLimiter = rateLimit({ windowMs: 15 * 60 * 1000, max: 200 });
const authLimiter   = rateLimit({ windowMs: 15 * 60 * 1000, max: 10, message: { error: 'Too many auth attempts' } });

app.use('/api/', globalLimiter);
app.use(morgan('combined'));
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true }));
app.use('/uploads', express.static(path.join(__dirname, '../uploads')));

app.get('/health', (_req, res) =>
  res.json({ status: 'ok', timestamp: new Date().toISOString(), service: 'DigiBuddy API' })
);

app.use('/api/auth',     authLimiter, authRoutes);
app.use('/api/helpers',  helperRoutes);
app.use('/api/bookings', bookingRoutes);
app.use('/api/ratings',  ratingRoutes);
app.use('/api/chat',     chatRoutes);
app.use('/api/users',    userRoutes);

app.use('*', (_req, res) => res.status(404).json({ error: 'Route not found' }));
app.use((err: Error, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
  console.error(err.stack);
  res.status(500).json({ error: 'Internal server error' });
});

export default app;
