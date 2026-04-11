import { Request, Response } from 'express';
import { prisma } from '../utils/prisma';
import { calculateDistance } from '../utils/geo';
import { AuthRequest } from '../middleware/auth';
import { logger } from '../utils/logger';

const helperInclude = { user: { select: { id: true, name: true, avatar: true } } };

const parseSkills = (raw: string): string[] => {
  try { return JSON.parse(raw); } catch { return []; }
};

const mapHelper = (h: any, userLat: number, userLng: number) => ({
  id: h.id, userId: h.userId,
  name: h.user?.name, avatar: h.user?.avatar,
  bio: h.bio, skills: parseSkills(h.skills),
  hourlyRate: h.hourlyRate, isAvailable: h.isAvailable,
  workAddress: h.workAddress, workLatitude: h.workLatitude, workLongitude: h.workLongitude,
  avgRating: h.avgRating, ratingCount: h.ratingCount, totalSessions: h.totalSessions,
  distance: h.workLatitude && h.workLongitude
    ? calculateDistance(userLat, userLng, h.workLatitude, h.workLongitude)
    : null,
});

export async function getNearbyHelpers(req: Request, res: Response) {
  try {
    const { lat, lng, radius = '10', skills, page = '1', limit = '20' } = req.query;
    if (!lat || !lng) return res.status(400).json({ error: 'lat and lng required' });
    const [uLat, uLng, r, p, l] = [+lat, +lng, +radius, +page, +limit];
    const skillFilter = skills ? String(skills).split(',').map(s => s.trim().toLowerCase()) : null;

    const helpers = await prisma.helperProfile.findMany({
      where: { isAvailable: true, workLatitude: { not: null }, workLongitude: { not: null } },
      include: helperInclude, skip: (p - 1) * l, take: l,
    });

    const nearby = helpers
      .filter(h => h.workLatitude && h.workLongitude && calculateDistance(uLat, uLng, h.workLatitude!, h.workLongitude!) <= r)
      .filter(h => !skillFilter || skillFilter.some(s => parseSkills(h.skills).map(k => k.toLowerCase()).includes(s)))
      .map(h => mapHelper(h, uLat, uLng))
      .sort((a, b) => (a.distance ?? 99) - (b.distance ?? 99));

    res.json({ helpers: nearby, total: nearby.length });
  } catch (err) { logger.error('getNearbyHelpers:', err); res.status(500).json({ error: 'Failed' }); }
}

export async function searchHelpers(req: Request, res: Response) {
  try {
    const { lat, lng, radius = '15', skills } = req.query;
    if (!lat || !lng) return res.status(400).json({ error: 'lat and lng required' });
    const [sLat, sLng, r] = [+lat, +lng, +radius];
    const skillFilter = skills ? String(skills).split(',').map(s => s.trim().toLowerCase()) : null;

    const helpers = await prisma.helperProfile.findMany({
      where: { workLatitude: { not: null }, workLongitude: { not: null } },
      include: helperInclude,
    });

    const results = helpers
      .filter(h => h.workLatitude && h.workLongitude && calculateDistance(sLat, sLng, h.workLatitude!, h.workLongitude!) <= r)
      .filter(h => !skillFilter || skillFilter.some(s => parseSkills(h.skills).map(k => k.toLowerCase()).includes(s)))
      .map(h => mapHelper(h, sLat, sLng))
      .sort((a, b) => (a.distance ?? 99) - (b.distance ?? 99));

    res.json({ helpers: results, total: results.length, searchLat: sLat, searchLng: sLng });
  } catch (err) { logger.error('searchHelpers:', err); res.status(500).json({ error: 'Search failed' }); }
}

export async function getHelperById(req: Request, res: Response) {
  try {
    const helper = await prisma.helperProfile.findUnique({
      where: { id: req.params.id },
      include: {
        user: { select: { id: true, name: true, avatar: true, createdAt: true } },
        ratings: { include: { customer: { select: { name: true, avatar: true } } }, orderBy: { createdAt: 'desc' }, take: 10 },
      },
    });
    if (!helper) return res.status(404).json({ error: 'Helper not found' });
    res.json({ ...helper, skills: parseSkills(helper.skills) });
  } catch (err) { logger.error('getHelperById:', err); res.status(500).json({ error: 'Failed' }); }
}

export async function updateHelperProfile(req: AuthRequest, res: Response) {
  try {
    const { bio, skills, hourlyRate } = req.body;
    const skillsJson = skills ? JSON.stringify(skills) : undefined;
    const h = await prisma.helperProfile.update({
      where: { userId: req.user!.id },
      data: { bio, ...(skillsJson !== undefined && { skills: skillsJson }), hourlyRate },
    });
    res.json({ ...h, skills: parseSkills(h.skills) });
  } catch (err) { logger.error('updateHelperProfile:', err); res.status(500).json({ error: 'Failed' }); }
}

export async function updateAvailability(req: AuthRequest, res: Response) {
  try {
    const h = await prisma.helperProfile.update({ where: { userId: req.user!.id }, data: { isAvailable: req.body.isAvailable } });
    res.json({ isAvailable: h.isAvailable });
  } catch (err) { logger.error('updateAvailability:', err); res.status(500).json({ error: 'Failed' }); }
}

export async function updateWorkLocation(req: AuthRequest, res: Response) {
  try {
    const { workAddress, workLatitude, workLongitude, workRadius } = req.body;
    const h = await prisma.helperProfile.update({
      where: { userId: req.user!.id },
      data: { workAddress, workLatitude, workLongitude, workRadius },
    });
    res.json({ workAddress: h.workAddress, workLatitude: h.workLatitude, workLongitude: h.workLongitude, workRadius: h.workRadius });
  } catch (err) { logger.error('updateWorkLocation:', err); res.status(500).json({ error: 'Failed' }); }
}
