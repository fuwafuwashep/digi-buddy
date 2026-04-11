import { prisma } from './prisma';

export const generateOtp = () => Math.floor(100000 + Math.random() * 900000).toString();

export async function createOtp(userId: string, type: 'PHONE' | 'EMAIL'): Promise<string> {
  await prisma.otpCode.updateMany({ where: { userId, type, isUsed: false }, data: { isUsed: true } });
  const code = generateOtp();
  const expiresAt = new Date(Date.now() + 10 * 60 * 1000);
  await prisma.otpCode.create({ data: { userId, code, type, expiresAt } });
  return code;
}

export async function verifyOtp(userId: string, code: string, type: 'PHONE' | 'EMAIL'): Promise<boolean> {
  const otp = await prisma.otpCode.findFirst({
    where: { userId, code, type, isUsed: false, expiresAt: { gt: new Date() } },
  });
  if (!otp) return false;
  await prisma.otpCode.update({ where: { id: otp.id }, data: { isUsed: true } });
  return true;
}

export async function sendSmsOtp(phone: string, code: string): Promise<void> {
  if (process.env.TWILIO_ACCOUNT_SID && process.env.TWILIO_AUTH_TOKEN) {
    const twilio = require('twilio')(process.env.TWILIO_ACCOUNT_SID, process.env.TWILIO_AUTH_TOKEN);
    await twilio.messages.create({
      body: `Your DigiBuddy verification code is: ${code}. Valid for 10 minutes.`,
      from: process.env.TWILIO_PHONE_NUMBER,
      to: phone,
    });
  } else {
    // Dev mode — log the code instead of sending SMS
    console.log(`\n🔑 [DEV OTP] Phone: ${phone} → Code: ${code}\n`);
  }
}
