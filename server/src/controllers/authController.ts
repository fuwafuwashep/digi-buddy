import { Request, Response } from 'express';
import bcrypt from 'bcryptjs';
import { prisma } from '../utils/prisma';
import { signToken, signRefresh, verifyRefresh } from '../utils/jwt';
import { createOtp, verifyOtp as verifyCode, sendSmsOtp } from '../utils/otp';
import { logger } from '../utils/logger';
import { AuthRequest } from '../middleware/auth';

const userSelect = { id: true, phone: true, email: true, name: true, role: true, avatar: true, isVerified: true };

// POST /api/auth/send-otp
export async function sendOtp(req: Request, res: Response) {
  try {
    const { phone } = req.body;
    if (!phone) return res.status(400).json({ error: 'Phone number required' });

    let user = await prisma.user.findUnique({ where: { phone } });
    if (!user) user = await prisma.user.create({ data: { phone, name: 'New User', role: 'CUSTOMER' } });

    const code = await createOtp(user.id, 'PHONE');
    await sendSmsOtp(phone, code);
    const devCode = process.env.NODE_ENV !== 'production' ? code : undefined;
    res.json({ message: 'OTP sent', userId: user.id, devCode });
  } catch (err) { logger.error('sendOtp:', err); res.status(500).json({ error: 'Failed to send OTP' }); }
}

// POST /api/auth/verify-otp
export async function verifyOtp(req: Request, res: Response) {
  try {
    const { userId, code } = req.body;
    if (!userId || !code) return res.status(400).json({ error: 'userId and code required' });

    const isValid = await verifyCode(userId, code, 'PHONE');
    if (!isValid) return res.status(400).json({ error: 'Invalid or expired OTP' });

    const user = await prisma.user.update({ where: { id: userId }, data: { isVerified: true }, select: userSelect });
    res.json({ user, accessToken: signToken({ userId: user.id, role: user.role }), refreshToken: signRefresh({ userId: user.id, role: user.role }) });
  } catch (err) { logger.error('verifyOtp:', err); res.status(500).json({ error: 'Verification failed' }); }
}

// POST /api/auth/register
export async function register(req: Request, res: Response) {
  try {
    const { email, password, name, role = 'CUSTOMER' } = req.body;
    if (!email || !name) return res.status(400).json({ error: 'Email and name required' });

    const existing = await prisma.user.findUnique({ where: { email } });
    if (existing) return res.status(409).json({ error: 'Email already registered' });

    const hashedPassword = password ? await bcrypt.hash(password, 12) : undefined;
    const finalRole = role === 'HELPER' ? 'HELPER' : 'CUSTOMER';
    const user = await prisma.user.create({
      data: { email, name, role: finalRole, password: hashedPassword, isVerified: false },
      select: userSelect,
    });

    if (finalRole === 'HELPER') {
      await prisma.helperProfile.create({ data: { userId: user.id } });
    }

    res.status(201).json({ user, accessToken: signToken({ userId: user.id, role: user.role }), refreshToken: signRefresh({ userId: user.id, role: user.role }) });
  } catch (err) { logger.error('register:', err); res.status(500).json({ error: 'Registration failed' }); }
}

// POST /api/auth/login
export async function login(req: Request, res: Response) {
  try {
    const { email, password } = req.body;
    if (!email) return res.status(400).json({ error: 'Email required' });
    const user = await prisma.user.findUnique({ where: { email }, select: { ...userSelect, password: true } });
    if (!user) return res.status(401).json({ error: 'Invalid credentials' });

    if (user.password && password) {
      const match = await bcrypt.compare(password, user.password);
      if (!match) return res.status(401).json({ error: 'Invalid credentials' });
    }

    const { password: _, ...safeUser } = user;
    res.json({ user: safeUser, accessToken: signToken({ userId: user.id, role: user.role }), refreshToken: signRefresh({ userId: user.id, role: user.role }) });
  } catch (err) { logger.error('login:', err); res.status(500).json({ error: 'Login failed' }); }
}

// POST /api/auth/refresh
export async function refreshToken(req: Request, res: Response) {
  try {
    const { refreshToken } = req.body;
    if (!refreshToken) return res.status(400).json({ error: 'Refresh token required' });
    const payload = verifyRefresh(refreshToken);
    const user = await prisma.user.findUnique({ where: { id: payload.userId } });
    if (!user?.isActive) return res.status(401).json({ error: 'User not found' });
    res.json({ accessToken: signToken({ userId: user.id, role: user.role }) });
  } catch { res.status(401).json({ error: 'Invalid refresh token' }); }
}

// POST /api/auth/complete-profile
export async function completeProfile(req: Request, res: Response) {
  try {
    const { userId, name, role } = req.body;
    const finalRole = role === 'HELPER' ? 'HELPER' : 'CUSTOMER';
    const user = await prisma.user.update({ where: { id: userId }, data: { name, role: finalRole }, select: userSelect });

    if (finalRole === 'HELPER') {
      await prisma.helperProfile.upsert({ where: { userId }, create: { userId }, update: {} });
    }

    res.json({ user, accessToken: signToken({ userId: user.id, role: user.role }), refreshToken: signRefresh({ userId: user.id, role: user.role }) });
  } catch (err) { logger.error('completeProfile:', err); res.status(500).json({ error: 'Failed' }); }
}
