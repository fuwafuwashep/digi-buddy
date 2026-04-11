import { Response } from 'express';
import { prisma } from '../utils/prisma';
import { AuthRequest } from '../middleware/auth';
import { getIO } from '../socket';
import { logger } from '../utils/logger';

const bookingInclude = {
  customer: { select: { id: true, name: true, avatar: true, phone: true } },
  helper: { include: { user: { select: { id: true, name: true, avatar: true } } } },
  chatRoom: { select: { id: true } },
  rating: { select: { stars: true, comment: true } },
};

export async function createBooking(req: AuthRequest, res: Response) {
  try {
    const { helperId, issue, meetAddress, meetLatitude, meetLongitude, meetTime } = req.body;
    const booking = await prisma.booking.create({
      data: { customerId: req.user!.id, helperId, issue, meetAddress, meetLatitude, meetLongitude, meetTime: meetTime ? new Date(meetTime) : undefined, status: 'PENDING' },
      include: bookingInclude,
    });
    const chatRoom = await prisma.chatRoom.create({ data: { bookingId: booking.id } });

    getIO()?.to(`user:${booking.helper.userId}`).emit('booking:new', {
      bookingId: booking.id, customerId: req.user!.id, issue: booking.issue, meetAddress: booking.meetAddress,
      customer: { name: booking.customer.name },
    });

    res.status(201).json({ ...booking, chatRoomId: chatRoom.id });
  } catch (err) { logger.error('createBooking:', err); res.status(500).json({ error: 'Failed' }); }
}

export async function getMyBookings(req: AuthRequest, res: Response) {
  try {
    const { status, role } = req.query;
    const hp = role === 'helper' ? await prisma.helperProfile.findUnique({ where: { userId: req.user!.id } }) : null;
    const where: any = hp ? { helperId: hp.id } : { customerId: req.user!.id };
    if (status) where.status = status;
    const bookings = await prisma.booking.findMany({ where, include: bookingInclude, orderBy: { createdAt: 'desc' } });
    res.json(bookings);
  } catch (err) { logger.error('getMyBookings:', err); res.status(500).json({ error: 'Failed' }); }
}

export async function getBookingById(req: AuthRequest, res: Response) {
  try {
    const booking = await prisma.booking.findUnique({ where: { id: req.params.id }, include: bookingInclude });
    if (!booking) return res.status(404).json({ error: 'Booking not found' });
    res.json(booking);
  } catch (err) { logger.error('getBookingById:', err); res.status(500).json({ error: 'Failed' }); }
}

export async function updateBookingStatus(req: AuthRequest, res: Response) {
  try {
    const { status } = req.body;
    const booking = await prisma.booking.update({
      where: { id: req.params.id },
      data: { status, ...(status === 'COMPLETED' ? { completedAt: new Date() } : {}), ...(status === 'IN_PROGRESS' ? { startedAt: new Date() } : {}) },
      include: { helper: { include: { user: true } } },
    });
    getIO()?.to(`user:${booking.customerId}`).emit('booking:status', { bookingId: booking.id, status: booking.status });
    if (status === 'COMPLETED') {
      await prisma.helperProfile.update({ where: { id: booking.helperId }, data: { totalSessions: { increment: 1 } } });
    }
    res.json(booking);
  } catch (err) { logger.error('updateBookingStatus:', err); res.status(500).json({ error: 'Failed' }); }
}

export async function updateMeetLocation(req: AuthRequest, res: Response) {
  try {
    const { meetAddress, meetLatitude, meetLongitude, meetTime } = req.body;
    const b = await prisma.booking.update({
      where: { id: req.params.id },
      data: { meetAddress, meetLatitude, meetLongitude, meetTime: meetTime ? new Date(meetTime) : undefined },
    });
    res.json(b);
  } catch (err) { logger.error('updateMeetLocation:', err); res.status(500).json({ error: 'Failed' }); }
}
