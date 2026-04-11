import { PrismaClient } from '@prisma/client';
import bcrypt from 'bcryptjs';

const prisma = new PrismaClient();

async function main() {
  console.log('🌱 Seeding DigiBuddy database...');

  // ─── Clean slate ────────────────────────────────────────────────────────────
  await prisma.chatMessage.deleteMany();
  await prisma.chatRoom.deleteMany();
  await prisma.rating.deleteMany();
  await prisma.booking.deleteMany();
  await prisma.otpCode.deleteMany();
  await prisma.helperProfile.deleteMany();
  await prisma.user.deleteMany();

  const password = await bcrypt.hash('password123', 12);

  // ─── Customer accounts ──────────────────────────────────────────────────────
  const customer1 = await prisma.user.create({
    data: {
      email: 'alice@example.com',
      name: 'Alice Johnson',
      role: 'CUSTOMER',
      password,
      isVerified: true,
      phone: '+15550001001',
    },
  });

  const customer2 = await prisma.user.create({
    data: {
      email: 'bob@example.com',
      name: 'Bob Smith',
      role: 'CUSTOMER',
      password,
      isVerified: true,
      phone: '+15550001002',
    },
  });

  // ─── Helper accounts ────────────────────────────────────────────────────────
  const helperUsers = [
    {
      email: 'carlos@example.com',
      name: 'Carlos Mendez',
      phone: '+15550002001',
      bio: 'Certified IT technician with 5+ years helping people set up phones, computers, and smart home devices. Patient, friendly, and always on time.',
      skills: ['Phone Setup', 'WiFi Troubleshooting', 'Smart Home', 'Computer Repair'],
      hourlyRate: 35,
      workAddress: 'Central Library, 123 Main St',
      workLatitude: 37.7749,
      workLongitude: -122.4194,
      workRadius: 10,
      avgRating: 4.8,
      ratingCount: 24,
      totalSessions: 31,
    },
    {
      email: 'diana@example.com',
      name: 'Diana Lee',
      phone: '+15550002002',
      bio: 'Software engineer by day, tech helper by choice. Specialize in iOS, macOS, and productivity apps. Quick to diagnose issues that others miss.',
      skills: ['iOS', 'macOS', 'Email Setup', 'Productivity Apps'],
      hourlyRate: 45,
      workAddress: 'Brew & Bean Coffee, 456 Oak Ave',
      workLatitude: 37.7849,
      workLongitude: -122.4094,
      workRadius: 8,
      avgRating: 4.9,
      ratingCount: 42,
      totalSessions: 55,
    },
    {
      email: 'ethan@example.com',
      name: 'Ethan Park',
      phone: '+15550002003',
      bio: 'Android enthusiast and data recovery specialist. No problem is too small. I love teaching seniors how to use their devices safely.',
      skills: ['Android', 'Data Recovery', 'Virus Removal', 'Senior Tech Help'],
      hourlyRate: 30,
      workAddress: 'Community Center, 789 Elm Blvd',
      workLatitude: 37.7649,
      workLongitude: -122.4294,
      workRadius: 12,
      avgRating: 4.7,
      ratingCount: 18,
      totalSessions: 22,
    },
    {
      email: 'fiona@example.com',
      name: 'Fiona Williams',
      phone: '+15550002004',
      bio: 'Network engineer with CCNA certification. Specialise in home WiFi setup, mesh networks, and router configuration. Remote support also available.',
      skills: ['WiFi Setup', 'Network Security', 'Router Config', 'Smart TV'],
      hourlyRate: 50,
      workAddress: 'Tech Hub Co-working, 101 Broad St',
      workLatitude: 37.7729,
      workLongitude: -122.4124,
      workRadius: 15,
      avgRating: 4.6,
      ratingCount: 11,
      totalSessions: 14,
      isAvailable: false,
    },
    {
      email: 'george@example.com',
      name: 'George Tanaka',
      phone: '+15550002005',
      bio: 'Friendly neighbourhood tech wizard. I fix everything: cracked screens (basic), broken apps, lost contacts, slow PCs — you name it.',
      skills: ['General IT', 'Phone Repair Guidance', 'App Troubleshooting', 'Windows'],
      hourlyRate: 25,
      workAddress: 'Starbucks, 555 Market St',
      workLatitude: 37.7799,
      workLongitude: -122.4174,
      workRadius: 5,
      avgRating: 4.5,
      ratingCount: 9,
      totalSessions: 12,
    },
  ];

  for (const data of helperUsers) {
    const {
      bio, skills, hourlyRate, workAddress, workLatitude, workLongitude,
      workRadius, avgRating, ratingCount, totalSessions, isAvailable = true,
      ...userData
    } = data;

    const user = await prisma.user.create({
      data: { ...userData, password, role: 'HELPER', isVerified: true },
    });

    await prisma.helperProfile.create({
      data: {
        userId: user.id,
        bio,
        skills: JSON.stringify(skills),
        hourlyRate,
        workAddress,
        workLatitude,
        workLongitude,
        workRadius,
        avgRating,
        ratingCount,
        totalSessions,
        isAvailable,
      },
    });

    console.log(`  ✅ Helper created: ${data.name}`);
  }

  // ─── Sample booking + rating ─────────────────────────────────────────────
  const carlosProfile = await prisma.helperProfile.findFirst({
    where: { user: { email: 'carlos@example.com' } },
  });

  if (carlosProfile) {
    const booking = await prisma.booking.create({
      data: {
        customerId: customer1.id,
        helperId: carlosProfile.id,
        issue: 'My iPhone keeps dropping WiFi. Tried everything!',
        meetAddress: 'Central Library, 123 Main St',
        meetLatitude: 37.7749,
        meetLongitude: -122.4194,
        status: 'COMPLETED',
        startedAt: new Date(Date.now() - 3 * 86400000),
        completedAt: new Date(Date.now() - 3 * 86400000 + 3600000),
      },
    });

    await prisma.chatRoom.create({ data: { bookingId: booking.id } });

    await prisma.rating.create({
      data: {
        bookingId: booking.id,
        customerId: customer1.id,
        helperId: carlosProfile.id,
        stars: 5,
        comment: 'Carlos was amazing! Fixed my WiFi issue in under 20 minutes. Super patient and explained everything clearly.',
      },
    });

    console.log('  ✅ Sample booking + rating created');
  }

  console.log('\n✅ Seed complete!');
  console.log('\nTest credentials (password: password123):');
  console.log('  Customer: alice@example.com');
  console.log('  Customer: bob@example.com');
  console.log('  Helper:   carlos@example.com');
  console.log('  Helper:   diana@example.com');
  console.log('  Helper:   ethan@example.com');
}

main()
  .catch((e) => { console.error('Seed failed:', e); process.exit(1); })
  .finally(async () => { await prisma.$disconnect(); });
