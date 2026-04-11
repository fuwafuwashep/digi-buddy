import { Router } from 'express';
import { authenticate, AuthRequest } from '../middleware/auth';
import { prisma } from '../utils/prisma';
const r = Router();
r.use(authenticate);
r.get('/me', async (req: AuthRequest, res) => {
  try {
    const user = await prisma.user.findUnique({
      where: { id: req.user!.id },
      select: { id: true, name: true, email: true, phone: true, avatar: true, role: true, isVerified: true, createdAt: true, helperProfile: true },
    });
    res.json(user);
  } catch { res.status(500).json({ error: 'Failed to fetch user' }); }
});
r.put('/me', async (req: AuthRequest, res) => {
  try {
    const { name, avatar } = req.body;
    const user = await prisma.user.update({
      where: { id: req.user!.id },
      data: { name, avatar },
      select: { id: true, name: true, email: true, phone: true, avatar: true, role: true },
    });
    res.json(user);
  } catch { res.status(500).json({ error: 'Failed to update user' }); }
});
export default r;
