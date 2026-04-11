import { Router } from 'express';
import * as c from '../controllers/chatController';
import { authenticate } from '../middleware/auth';
const r = Router();
r.use(authenticate);
r.get('/rooms',                   c.getMyChatRooms);
r.get('/rooms/:roomId/messages',  c.getRoomMessages);
export default r;
