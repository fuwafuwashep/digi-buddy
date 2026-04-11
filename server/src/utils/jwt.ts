import jwt from 'jsonwebtoken';

const JWT_SECRET = process.env.JWT_SECRET || 'fallback-secret-change-in-production';
const JWT_EXPIRES_IN = process.env.JWT_EXPIRES_IN || '7d';
const JWT_REFRESH_SECRET = process.env.JWT_REFRESH_SECRET || 'refresh-secret-change-in-production';
const JWT_REFRESH_EXPIRES_IN = process.env.JWT_REFRESH_EXPIRES_IN || '30d';

export interface TokenPayload { userId: string; role: string; }

export const signToken    = (p: TokenPayload) => jwt.sign(p, JWT_SECRET, { expiresIn: JWT_EXPIRES_IN } as any);
export const signRefresh  = (p: TokenPayload) => jwt.sign(p, JWT_REFRESH_SECRET, { expiresIn: JWT_REFRESH_EXPIRES_IN } as any);
export const verifyToken  = (t: string) => jwt.verify(t, JWT_SECRET) as TokenPayload;
export const verifyRefresh= (t: string) => jwt.verify(t, JWT_REFRESH_SECRET) as TokenPayload;
