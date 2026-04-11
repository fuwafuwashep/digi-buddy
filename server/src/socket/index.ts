import { Server as HttpServer } from 'http';
import { Server as SocketServer } from 'socket.io';
import { verifyToken } from '../utils/jwt';
import { prisma } from '../utils/prisma';
import { logger } from '../utils/logger';

let io: SocketServer;

export function initSocket(httpServer: HttpServer) {
  io = new SocketServer(httpServer, {
    cors: {
      origin: (process.env.ALLOWED_ORIGINS || 'http://localhost:3000').split(','),
      methods: ['GET', 'POST'],
    },
  });

  io.use(async (socket, next) => {
    try {
      const token = socket.handshake.auth.token;
      if (!token) return next(new Error('Authentication required'));
      const payload = verifyToken(token);
      (socket as any).userId = payload.userId;
      next();
    } catch { next(new Error('Invalid token')); }
  });

  io.on('connection', (socket) => {
    const userId = (socket as any).userId;
    logger.info(`Socket connected: ${socket.id} (user: ${userId})`);
    socket.join(`user:${userId}`);

    socket.on('join:room', async (roomId: string) => {
      try {
        const room = await prisma.chatRoom.findUnique({
          where: { id: roomId }, include: { booking: true }
        });
        if (!room) return;
        const hp = await prisma.helperProfile.findUnique({ where: { id: room.booking.helperId } });
        if (room.booking.customerId === userId || hp?.userId === userId) {
          socket.join(`room:${roomId}`);
          socket.emit('room:joined', { roomId });
        }
      } catch (err) { logger.error('join:room error:', err); }
    });

    socket.on('message:send', async (data: { roomId: string; content: string; type?: string }) => {
      try {
        const { roomId, content, type = 'TEXT' } = data;
        const message = await prisma.chatMessage.create({
          data: { roomId, senderId: userId, content, type: type as any },
          include: { sender: { select: { id: true, name: true, avatar: true } } },
        });
        io.to(`room:${roomId}`).emit('message:new', {
          id: message.id, content: message.content, type: message.type,
          sender: message.sender, senderId: userId, roomId, createdAt: message.createdAt,
        });
      } catch (err) {
        logger.error('message:send error:', err);
        socket.emit('error', { message: 'Failed to send message' });
      }
    });

    socket.on('typing:start', (roomId: string) => socket.to(`room:${roomId}`).emit('typing:user', { userId, isTyping: true }));
    socket.on('typing:stop',  (roomId: string) => socket.to(`room:${roomId}`).emit('typing:user', { userId, isTyping: false }));

    socket.on('helper:availability', async (isAvailable: boolean) => {
      try {
        await prisma.helperProfile.update({ where: { userId }, data: { isAvailable } });
        io.emit('helper:status:changed', { userId, isAvailable });
      } catch (err) { logger.error('helper:availability error:', err); }
    });

    socket.on('booking:update', async (data: { bookingId: string; status: string }) => {
      try {
        const booking = await prisma.booking.findUnique({ where: { id: data.bookingId } });
        if (!booking) return;
        io.to(`user:${booking.customerId}`).emit('booking:status', { bookingId: data.bookingId, status: data.status });
      } catch (err) { logger.error('booking:update error:', err); }
    });

    socket.on('disconnect', () => logger.info(`Socket disconnected: ${socket.id}`));
  });

  return io;
}

export const getIO = () => io;
