import { Response } from 'express';
import { prisma } from '../utils/prisma';
import { AuthRequest } from '../middleware/auth';
import { logger } from '../utils/logger';

export async function getMyChatRooms(req: AuthRequest, res: Response) {
  try {
    const userId = req.user!.id;
    const hp = await prisma.helperProfile.findUnique({ where: { userId } });
    const rooms = await prisma.chatRoom.findMany({
      where: { booking: { OR: [{ customerId: userId }, ...(hp ? [{ helperId: hp.id }] : [])] } },
      include: {
        booking: { include: {
          customer: { select: { id: true, name: true, avatar: true } },
          helper: { include: { user: { select: { id: true, name: true, avatar: true } } } },
        }},
        messages: { orderBy: { createdAt: 'desc' }, take: 1 },
      },
      orderBy: { updatedAt: 'desc' },
    });
    res.json(rooms);
  } catch (err) { logger.error('getMyChatRooms:', err); res.status(500).json({ error: 'Failed' }); }
}

export async function getRoomMessages(req: AuthRequest, res: Response) {
  try {
    const { roomId } = req.params;
    const { page = '1', limit = '50' } = req.query;
    const [p, l] = [+page, +limit];
    const messages = await prisma.chatMessage.findMany({
      where: { roomId },
      include: { sender: { select: { id: true, name: true, avatar: true } } },
      orderBy: { createdAt: 'asc' },
      skip: (p - 1) * l,
      take: l,
    });
    await prisma.chatMessage.updateMany({
      where: { roomId, senderId: { not: req.user!.id }, isRead: false },
      data: { isRead: true },
    });
    res.json(messages);
  } catch (err) { logger.error('getRoomMessages:', err); res.status(500).json({ error: 'Failed' }); }
}
