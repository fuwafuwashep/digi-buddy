import { Request, Response } from 'express';
import { prisma } from '../utils/prisma';
import { AuthRequest } from '../middleware/auth';
import { logger } from '../utils/logger';

export async function getHelperRatings(req: Request, res: Response) {
  try {
    const { helperId } = req.params;
    const { page = '1', limit = '10' } = req.query;
    const [pageNum, limitNum] = [+page, +limit];
    const [ratings, total] = await prisma.$transaction([
      prisma.rating.findMany({ where: { helperId }, include: { customer: { select: { name: true, avatar: true } } }, orderBy: { createdAt: 'desc' }, skip: (pageNum - 1) * limitNum, take: limitNum }),
      prisma.rating.count({ where: { helperId } }),
    ]);
    res.json({ ratings, total, page: pageNum, limit: limitNum });
  } catch (err) { logger.error('getHelperRatings:', err); res.status(500).json({ error: 'Failed' }); }
}

export async function createRating(req: AuthRequest, res: Response) {
  try {
    const { bookingId, stars, comment } = req.body;
    if (stars < 1 || stars > 5) return res.status(400).json({ error: 'Stars must be 1–5' });
    const booking = await prisma.booking.findUnique({ where: { id: bookingId } });
    if (!booking) return res.status(404).json({ error: 'Booking not found' });
    if (booking.customerId !== req.user!.id) return res.status(403).json({ error: 'Forbidden' });
    if (booking.status !== 'COMPLETED') return res.status(400).json({ error: 'Can only rate completed bookings' });

    const rating = await prisma.rating.create({ data: { bookingId, customerId: req.user!.id, helperId: booking.helperId, stars, comment } });

    // Update helper average
    const all = await prisma.rating.findMany({ where: { helperId: booking.helperId }, select: { stars: true } });
    const avg = all.reduce((s, r) => s + r.stars, 0) / all.length;
    await prisma.helperProfile.update({ where: { id: booking.helperId }, data: { avgRating: avg, ratingCount: all.length } });

    res.status(201).json(rating);
  } catch (err) { logger.error('createRating:', err); res.status(500).json({ error: 'Failed' }); }
}
