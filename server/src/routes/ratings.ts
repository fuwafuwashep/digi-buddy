import { Router } from 'express';
import * as c from '../controllers/ratingController';
import { authenticate } from '../middleware/auth';
const r = Router();
r.get('/helper/:helperId', c.getHelperRatings);
r.post('/', authenticate,  c.createRating);
export default r;
