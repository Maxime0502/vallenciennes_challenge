package sample;

import robocode.*;
import robocode.util.Utils;
import robocode.Rules;


import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
/**
 * DarkGrayBotUltimate
 *
 * Objectif : bot "très dur à battre" pour Robocode classique.
 * - Duel (getOthers()==1) : Wave Surfing (mouvement) + GuessFactor gun (visée)
 * - Mêlée                : Minimum Risk (mouvement) + GuessFactor gun (visée)
 *
 * NOTE :
 * - Ce code est original (pas une copie de bots top-tier), mais s'appuie sur des techniques standard Robocode.
 * - Tu peux renommer la classe et le fichier si tu veux garder le nom DarkGrayBot.
 */
public class DarkGrayBot extends AdvancedRobot {

    // ------------------ Constantes / paramètres ------------------

    private static final int BINS = 47;
    private static final int DIST_SEGS = 5;
    private static final int VEL_SEGS  = 5;
    private static final int WALL_SEGS = 2;

    private static final double BOT_RADIUS  = 18.0;
    private static final double WALL_MARGIN = 60.0;   // distance de sécurité pour la "safeRect"
    private static final double WALL_STICK  = 150.0;  // longueur du "blind man's stick" (wall smoothing)

    // Stats globales (persistent entre rounds via static)
    private static final double[][][][] GLOBAL_GUN_STATS =
            new double[DIST_SEGS][VEL_SEGS][WALL_SEGS][BINS];

    // ------------------ Données runtime ------------------

    private final Map<String, Enemy> enemies = new HashMap<>();

    // Stats par ennemi (utile si on a assez de données)
    private final Map<String, double[][][][]> enemyStats = new HashMap<>();

    private Enemy target;

    // Waves ennemies (principalement utilisées en duel)
    private final List<EnemyWave> enemyWaves = new ArrayList<>();

    // Waves "à nous" pour apprendre après un hit/miss
    private final List<MyWave> myWaves = new ArrayList<>();

    // Danger bins pour le surfing
    private final double[] surfStats = new double[BINS];

    private double fieldW, fieldH;
    private Rectangle2D.Double safeRect;

    // Mouvement mêlée (minimum risk)
    private Point2D.Double lastDestination;
    private long lastDestinationTime = 0;

    // Petit état d'orbite / wander
    private int orbitDirection = 1;

    private final Random rng = new Random();

    // ------------------ Structures ------------------

    static class Enemy {
        String name;

        double x, y;
        double distance;
        double absBearing;

        double heading;
        double velocity;

        double energy = 100.0;
        double lastEnergy = 100.0;

        long lastSeen;
        boolean alive = true;

        long lastShotTime = -1000;
    }

    static class EnemyWave {
        Point2D.Double fireLocation;
        long fireTime;

        double bulletSpeed;
        double bulletPower;

        double directAngle;      // angle direct (shooter -> moi) au moment du tir
        double maxEscapeAngle;   // asin(8 / bulletSpeed)

        int direction;           // signe du mouvement latéral (ex: pour GF sign)
        String enemyName;

        double distanceTraveled;

        double remainingDistance(Point2D.Double p) {
            return p.distance(fireLocation) - distanceTraveled;
        }
    }

    static class MyWave {
        Point2D.Double fireLocation;
        long fireTime;

        double bulletSpeed;
        double bulletPower;

        double directAngle;      // shooter -> target au moment du tir
        double maxEscapeAngle;

        int direction;

        int distSeg, velSeg, wallSeg;
        String targetName;
    }

    // ------------------ Main loop ------------------

    public void run() {
        fieldW = getBattleFieldWidth();
        fieldH = getBattleFieldHeight();

        safeRect = new Rectangle2D.Double(
                WALL_MARGIN, WALL_MARGIN,
                fieldW - 2.0 * WALL_MARGIN,
                fieldH - 2.0 * WALL_MARGIN
        );

        setBodyColor(Color.DARK_GRAY);
        setGunColor(Color.BLACK);
        setRadarColor(Color.GRAY);
        setBulletColor(Color.CYAN);
        setScanColor(Color.WHITE);

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);

        // petit "prior" pour éviter des égalités constantes au début
        for (int i = 0; i < BINS; i++) {
            surfStats[i] = 1.0;
        }

        setTurnRadarRightRadians(Double.POSITIVE_INFINITY);

        while (true) {
            cleanupEnemies();
            chooseTarget();
            updateWaves();

            doRadar();
            doMovement();
            doGun();

            execute();
        }
    }

    // ------------------ Events ------------------

    public void onScannedRobot(ScannedRobotEvent e) {
        Enemy en = enemies.get(e.getName());
        if (en == null) {
            en = new Enemy();
            en.name = e.getName();
            enemies.put(en.name, en);
        }

        double absBearing = getHeadingRadians() + e.getBearingRadians();
        double ex = getX() + Math.sin(absBearing) * e.getDistance();
        double ey = getY() + Math.cos(absBearing) * e.getDistance();

        en.x = ex;
        en.y = ey;
        en.absBearing = absBearing;
        en.distance = e.getDistance();
        en.heading = e.getHeadingRadians();
        en.velocity = e.getVelocity();
        en.lastSeen = getTime();
        en.alive = true;

        double oldEnergy = en.energy;
        en.lastEnergy = oldEnergy;
        en.energy = e.getEnergy();

        double drop = oldEnergy - en.energy;
        if (drop > 0.09 && drop <= 3.01) {
            en.lastShotTime = getTime();
            // En duel : on crée une wave pour surfer
            if (getOthers() == 1) {
                addEnemyWave(en, drop);
            }
        }
    }

    public void onRobotDeath(RobotDeathEvent e) {
        Enemy en = enemies.get(e.getName());
        if (en != null) {
            en.alive = false;
        }
        if (target != null && target.name.equals(e.getName())) {
            target = null;
        }

        enemyWaves.removeIf(w -> w.enemyName.equals(e.getName()));
        myWaves.removeIf(w -> w.targetName.equals(e.getName()));
    }

    public void onHitByBullet(HitByBulletEvent e) {
        Bullet b = e.getBullet();
        if (b != null) {
            EnemyWave w = findMatchingEnemyWave(b.getName(), b);
            if (w != null) {
                int index = getFactorIndex(w, new Point2D.Double(getX(), getY()));
                updateStats(surfStats, index, 2.5);
                enemyWaves.remove(w);
            }
        }

        // Petit kick d'évasion
        orbitDirection = -orbitDirection;
        lastDestination = null;
    }

    public void onBulletHit(BulletHitEvent e) {
        Bullet b = e.getBullet();
        if (b == null) {
            return;
        }

        MyWave w = findMatchingMyWave(e.getName(), b);
        if (w != null) {
            Point2D.Double hitPoint = new Point2D.Double(b.getX(), b.getY());
            double bearing = absoluteBearing(w.fireLocation, hitPoint);
            double offset = Utils.normalRelativeAngle(bearing - w.directAngle);

            double gf = clamp(offset / w.maxEscapeAngle, -1.0, 1.0) * w.direction;
            int index = (int) Math.round((BINS - 1) / 2.0 * (gf + 1.0));
            index = (int) clamp(index, 0, BINS - 1);

            logGunHit(w, index);
            myWaves.remove(w);
        }

        Enemy en = enemies.get(e.getName());
        if (en != null) {
            en.energy = e.getEnergy();
        }
    }

    public void onBulletMissed(BulletMissedEvent e) {
        Bullet b = e.getBullet();
        if (b != null) {
            removeMyWaveForBullet(b);
        }
    }

    public void onBulletHitBullet(BulletHitBulletEvent e) {
        Bullet b = e.getBullet();
        if (b != null) {
            removeMyWaveForBullet(b);
        }
    }

    public void onHitWall(HitWallEvent e) {
        orbitDirection = -orbitDirection;
        lastDestination = null;
        setBack(100);
    }

    public void onHitRobot(HitRobotEvent e) {
        Enemy en = enemies.get(e.getName());
        if (en != null && en.alive && en.energy < 5.0 && getEnergy() > en.energy + 15.0) {
            // tentation de "finish" si énorme avantage
            setAhead(60);
        } else {
            setBack(80);
            orbitDirection = -orbitDirection;
            lastDestination = null;
        }
    }

    // ------------------ Radar ------------------

    private void doRadar() {
        // En mêlée (>=3 ennemis), on scanne large
        if (getOthers() >= 3) {
            setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
            return;
        }

        if (target != null && target.alive && getTime() - target.lastSeen <= 25) {
            double absBearing = absoluteBearing(getX(), getY(), target.x, target.y);
            double radarTurn = Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians());

            double dist = distance(getX(), getY(), target.x, target.y);
            double extra = Math.atan(36.0 / Math.max(1.0, dist));
            radarTurn += Math.signum(radarTurn) * extra;

            setTurnRadarRightRadians(radarTurn);
        } else {
            setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
        }
    }

    // ------------------ Targeting (choix de cible) ------------------

    private void cleanupEnemies() {
        for (Enemy e : enemies.values()) {
            if (e.alive && getTime() - e.lastSeen > 200) {
                e.alive = false;
            }
        }
    }

    private void chooseTarget() {
        if (getOthers() == 0) {
            target = null;
            return;
        }

        Enemy best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (Enemy e : enemies.values()) {
            if (!e.alive) continue;

            long age = getTime() - e.lastSeen;
            if (age > 70) continue;

            double dist = distance(getX(), getY(), e.x, e.y);

            double score = 0.0;
            score += 1100.0 / (dist + 80.0);
            score += (100.0 - Math.min(100.0, e.energy)) * 0.10;
            score += Math.max(0.0, 30.0 - age) * 0.06;

            if (getTime() - e.lastShotTime < 20) score += 1.4;
            if (dist < 160) score += 0.6;

            score -= crossfirePenalty(e);

            if (target != null && e.name.equals(target.name)) score += 0.25;

            if (score > bestScore) {
                bestScore = score;
                best = e;
            }
        }

        target = best;
    }

    private double crossfirePenalty(Enemy chosen) {
        if (getOthers() <= 1) return 0.0;

        Enemy other = null;
        for (Enemy e : enemies.values()) {
            if (e.alive && e != chosen && getTime() - e.lastSeen <= 50) {
                other = e;
                break;
            }
        }
        if (other == null) return 0.0;

        double a1 = absoluteBearing(getX(), getY(), chosen.x, chosen.y);
        double a2 = absoluteBearing(getX(), getY(), other.x, other.y);
        double diff = Math.abs(Utils.normalRelativeAngle(a1 - a2));

        if (diff < Math.toRadians(20)) return 2.5;
        if (diff < Math.toRadians(35)) return 1.1;
        return 0.0;
    }

    // ------------------ Gun (GuessFactor) ------------------

    private void doGun() {
        if (target == null || !target.alive) return;
        if (getTime() - target.lastSeen > 25) return;

        Point2D.Double myPos = new Point2D.Double(getX(), getY());
        Point2D.Double enemyPos = new Point2D.Double(target.x, target.y);

        double dist = myPos.distance(enemyPos);

        double firePower = computeFirePower(dist, target.energy, getOthers());
        double bulletSpeed = Rules.getBulletSpeed(firePower);

        double absBearing = absoluteBearing(myPos, enemyPos);

        // Lateral velocity -> signe
        double enemyLatVel = target.velocity * Math.sin(target.heading - absBearing);
        int direction = enemyLatVel >= 0 ? 1 : -1;

        double maxEscapeAngle = Math.asin(8.0 / bulletSpeed);

        int distSeg = getDistanceSeg(dist);
        int velSeg = getVelSeg(target.velocity);
        int wallSeg = getWallSeg(target.x, target.y);

        double[] bins = chooseBins(target.name, distSeg, velSeg, wallSeg);
        int bestIndex = mostVisitedBin(bins);

        double guessFactor = (bestIndex - (BINS - 1) / 2.0) / ((BINS - 1) / 2.0);
        double angleOffset = direction * guessFactor * maxEscapeAngle;

        double aimAngle = absBearing + angleOffset;
        double gunTurn = Utils.normalRelativeAngle(aimAngle - getGunHeadingRadians());
        setTurnGunRightRadians(gunTurn);

        double tolerance = Math.toRadians(dist < 150 ? 6.0 : dist < 320 ? 3.5 : 2.0);
        boolean aimOk = Math.abs(getGunTurnRemainingRadians()) < tolerance;

        if (aimOk && getGunHeat() == 0.0 && getEnergy() > firePower + 0.1) {
            Bullet b = setFireBullet(firePower);
            if (b != null) {
                MyWave w = new MyWave();
                w.fireLocation = myPos;
                w.fireTime = getTime();
                w.bulletPower = firePower;
                w.bulletSpeed = bulletSpeed;
                w.directAngle = absBearing;
                w.maxEscapeAngle = maxEscapeAngle;
                w.direction = direction;
                w.distSeg = distSeg;
                w.velSeg = velSeg;
                w.wallSeg = wallSeg;
                w.targetName = target.name;
                myWaves.add(w);
            }
        }
    }

    private double computeFirePower(double distance, double enemyEnergy, int othersRemaining) {
        double p;
        if (distance < 120) p = 3.0;
        else if (distance < 250) p = 2.2;
        else if (distance < 400) p = 1.7;
        else p = 1.2;

        // Mêlée : éviter de trop s'exposer
        if (othersRemaining > 1) {
            p = Math.min(p, 2.0);
            if (distance > 450) p = Math.min(p, 1.4);
        }

        if (enemyEnergy < 10) p = Math.max(p, 1.5);
        if (enemyEnergy < 4) p = Math.min(p, 1.2);

        // Conservation d'énergie
        if (getEnergy() < 20) p = Math.min(p, 1.6);
        if (getEnergy() < 10) p = Math.min(p, 1.0);
        if (getEnergy() < 5) p = Math.min(p, 0.6);

        return clamp(p, 0.1, 3.0);
    }

    private double[] chooseBins(String enemyName, int distSeg, int velSeg, int wallSeg) {
        double[][][][] es = enemyStats.get(enemyName);
        if (es == null) {
            es = new double[DIST_SEGS][VEL_SEGS][WALL_SEGS][BINS];
            enemyStats.put(enemyName, es);
        }

        double[] perEnemy = es[distSeg][velSeg][wallSeg];
        double sum = 0.0;
        for (double v : perEnemy) sum += v;

        // Si l'ennemi n'a pas assez de données, fallback sur global
        if (sum < 50.0) {
            return GLOBAL_GUN_STATS[distSeg][velSeg][wallSeg];
        }
        return perEnemy;
    }

    private int mostVisitedBin(double[] bins) {
        int best = BINS / 2;
        double bestV = bins[best];

        for (int i = 0; i < bins.length; i++) {
            if (bins[i] > bestV) {
                bestV = bins[i];
                best = i;
            }
        }
        return best;
    }

    private void logGunHit(MyWave w, int index) {
        // Global
        updateStats(GLOBAL_GUN_STATS[w.distSeg][w.velSeg][w.wallSeg], index, 3.0);

        // Par ennemi
        double[][][][] es = enemyStats.get(w.targetName);
        if (es == null) {
            es = new double[DIST_SEGS][VEL_SEGS][WALL_SEGS][BINS];
            enemyStats.put(w.targetName, es);
        }
        updateStats(es[w.distSeg][w.velSeg][w.wallSeg], index, 3.0);
    }

    // ------------------ Movement ------------------

    private void doMovement() {
        if (target == null || !target.alive) {
            wander();
            return;
        }

        if (getOthers() == 1) {
            doWaveSurfing();
        } else {
            doMinimumRiskMovement();
        }
    }

    private void doWaveSurfing() {
        EnemyWave surfWave = getClosestSurfableWave();
        if (surfWave != null) {
            int dir = surfDirection(surfWave);

            double angleToWave = absoluteBearing(surfWave.fireLocation, new Point2D.Double(getX(), getY()));
            double moveAngle = angleToWave + dir * (Math.PI / 2.0);
            moveAngle = wallSmooth(getX(), getY(), moveAngle, dir);

            setMaxVelocity(8.0);
            setBackAsFront(moveAngle, 130.0);
        } else {
            // fallback : simple perpendicular (reste mobile)
            double absBearing = absoluteBearing(getX(), getY(), target.x, target.y);

            if (rng.nextDouble() < 0.02) orbitDirection = -orbitDirection;

            double moveAngle = absBearing + orbitDirection * (Math.PI / 2.0);
            moveAngle = wallSmooth(getX(), getY(), moveAngle, orbitDirection);

            setMaxVelocity(8.0);
            setBackAsFront(moveAngle, 140.0);
        }
    }

    private void doMinimumRiskMovement() {
        Point2D.Double myPos = new Point2D.Double(getX(), getY());

        if (lastDestination != null && myPos.distance(lastDestination) < 30.0) {
            lastDestination = null;
        }

        if (lastDestination == null ||
                getTime() - lastDestinationTime > 35 ||
                Math.abs(getDistanceRemaining()) < 0.01) {

            lastDestination = findBestDestination();
            lastDestinationTime = getTime();
        }

        goTo(lastDestination);
    }

    private Point2D.Double findBestDestination() {
        Point2D.Double myPos = new Point2D.Double(getX(), getY());

        double bestRisk = Double.POSITIVE_INFINITY;
        Point2D.Double best = null;

        double baseRadius = 180.0;
        int tries = 36;

        for (int i = 0; i < tries; i++) {
            double ang = (2.0 * Math.PI * i / tries) + (rng.nextDouble() * 0.30 - 0.15);
            double radius = baseRadius + rng.nextDouble() * 120.0;

            Point2D.Double p = project(myPos, ang, radius);
            if (!safeRect.contains(p)) continue;

            double risk = riskAtPoint(p);

            // inertie : coût de rotation
            double turn = Math.abs(Utils.normalRelativeAngle(absoluteBearing(myPos, p) - getHeadingRadians()));
            risk += turn * 0.20;

            if (risk < bestRisk) {
                bestRisk = risk;
                best = p;
            }
        }

        if (best == null) {
            best = new Point2D.Double(fieldW / 2.0, fieldH / 2.0);
        }
        return best;
    }

    private double riskAtPoint(Point2D.Double p) {
        double risk = wallRisk(p);

        for (Enemy e : enemies.values()) {
            if (!e.alive) continue;

            long age = getTime() - e.lastSeen;
            if (age > 80) continue;

            double d = p.distance(e.x, e.y);
            double energyFactor = 40.0 + Math.min(100.0, e.energy);

            risk += energyFactor / (d * d);
            if (d < 120) risk += 2.0 / Math.max(1.0, d);
        }

        return risk;
    }

    private double wallRisk(Point2D.Double p) {
        double m = Math.min(Math.min(p.x, fieldW - p.x), Math.min(p.y, fieldH - p.y));
        if (m <= 0) return 1e9;

        double base = 1.0 / (m * m);
        if (m < WALL_MARGIN) base *= 8.0;
        return base;
    }

    // ------------------ Wave Surfing internals ------------------

    private void addEnemyWave(Enemy enemy, double bulletPower) {
        EnemyWave w = new EnemyWave();
        w.enemyName = enemy.name;

        w.fireLocation = new Point2D.Double(enemy.x, enemy.y);

        // Approximation : le tir a eu lieu entre le tick précédent et celui-ci
        w.fireTime = getTime() - 1;

        w.bulletPower = bulletPower;
        w.bulletSpeed = Rules.getBulletSpeed(bulletPower);

        Point2D.Double myPos = new Point2D.Double(getX(), getY());
        w.directAngle = absoluteBearing(w.fireLocation, myPos);

        // direction basée sur notre vitesse latérale au moment du scan (approx)
        double myLatVel = getVelocity() * Math.sin(getHeadingRadians() - w.directAngle);
        w.direction = myLatVel >= 0 ? 1 : -1;

        w.maxEscapeAngle = Math.asin(8.0 / w.bulletSpeed);

        w.distanceTraveled = w.bulletSpeed; // déjà ~1 tick
        enemyWaves.add(w);
    }

    private EnemyWave getClosestSurfableWave() {
        EnemyWave best = null;
        double bestRemaining = Double.POSITIVE_INFINITY;

        Point2D.Double myPos = new Point2D.Double(getX(), getY());

        for (EnemyWave w : enemyWaves) {
            double rem = w.remainingDistance(myPos);
            if (rem > 0 && rem < bestRemaining) {
                bestRemaining = rem;
                best = w;
            }
        }
        return best;
    }

    private int surfDirection(EnemyWave w) {
        double dangerLeft = checkDanger(w, -1);
        double dangerRight = checkDanger(w, 1);

        if (dangerLeft < dangerRight) return -1;
        if (dangerRight < dangerLeft) return 1;
        return rng.nextBoolean() ? -1 : 1;
    }

    private double checkDanger(EnemyWave w, int direction) {
        Point2D.Double predicted = predictPosition(w, direction);
        int index = getFactorIndex(w, predicted);
        return surfStats[index];
    }

    private int getFactorIndex(EnemyWave w, Point2D.Double location) {
        double offsetAngle = Utils.normalRelativeAngle(
                absoluteBearing(w.fireLocation, location) - w.directAngle
        );

        double factor = clamp(offsetAngle / w.maxEscapeAngle, -1.0, 1.0) * w.direction;
        int index = (int) Math.round((BINS - 1) / 2.0 * (factor + 1.0));
        return (int) clamp(index, 0, BINS - 1);
    }

    private Point2D.Double predictPosition(EnemyWave w, int direction) {
        Point2D.Double pos = new Point2D.Double(getX(), getY());
        double heading = getHeadingRadians();
        double velocity = getVelocity();

        long startTime = getTime();
        int moveDir = 1;

        for (int i = 0; i < 500; i++) {
            double angleToWave = absoluteBearing(w.fireLocation, pos);
            double moveAngle = angleToWave + direction * (Math.PI / 2.0);

            moveAngle = wallSmooth(pos.x, pos.y, moveAngle, direction);

            double turnAngle = Utils.normalRelativeAngle(moveAngle - heading);
            if (Math.cos(turnAngle) < 0) {
                turnAngle = Utils.normalRelativeAngle(turnAngle + Math.PI);
                moveDir = -1;
            } else {
                moveDir = 1;
            }

            double maxTurn = Rules.getTurnRateRadians(Math.abs(velocity));
            heading = Utils.normalRelativeAngle(heading + clamp(turnAngle, -maxTurn, maxTurn));
            velocity = nextVelocity(velocity, moveDir);

            pos.x += Math.sin(heading) * velocity;
            pos.y += Math.cos(heading) * velocity;

            pos.x = clamp(pos.x, BOT_RADIUS, fieldW - BOT_RADIUS);
            pos.y = clamp(pos.y, BOT_RADIUS, fieldH - BOT_RADIUS);

            double waveTravel = (startTime + i - w.fireTime) * w.bulletSpeed;
            if (pos.distance(w.fireLocation) <= waveTravel + w.bulletSpeed) {
                break;
            }
        }

        return pos;
    }

    private double nextVelocity(double velocity, int direction) {
        if (direction == 1) {
            if (velocity < 0) velocity += 2;
            else velocity = Math.min(8, velocity + 1);
        } else {
            if (velocity > 0) velocity -= 2;
            else velocity = Math.max(-8, velocity - 1);
        }
        return velocity;
    }

    // ------------------ Bookkeeping des waves ------------------

    private void updateWaves() {
        long now = getTime();
        Point2D.Double myPos = new Point2D.Double(getX(), getY());

        // Waves ennemies
        Iterator<EnemyWave> it = enemyWaves.iterator();
        while (it.hasNext()) {
            EnemyWave w = it.next();
            w.distanceTraveled = (now - w.fireTime) * w.bulletSpeed;

            if (w.distanceTraveled > myPos.distance(w.fireLocation) + 80) {
                it.remove();
            }
        }

        // Waves à nous : TTL (si aucun event n'arrive)
        double maxDist = Math.hypot(fieldW, fieldH) + 80;
        Iterator<MyWave> it2 = myWaves.iterator();
        while (it2.hasNext()) {
            MyWave w = it2.next();
            if ((now - w.fireTime) * w.bulletSpeed > maxDist) {
                it2.remove();
            } else {
                Enemy en = enemies.get(w.targetName);
                if (en == null || !en.alive) it2.remove();
            }
        }
    }

    private MyWave findMatchingMyWave(String victimName, Bullet b) {
        long now = getTime();
        MyWave best = null;
        double bestDiff = Double.POSITIVE_INFINITY;

        Point2D.Double bulletPoint = new Point2D.Double(b.getX(), b.getY());

        for (MyWave w : myWaves) {
            if (!w.targetName.equals(victimName)) continue;
            if (Math.abs(w.bulletSpeed - b.getVelocity()) > 0.2) continue;

            double traveled = (now - w.fireTime) * w.bulletSpeed;
            double actual = w.fireLocation.distance(bulletPoint);
            double diff = Math.abs(actual - traveled);

            if (diff < bestDiff) {
                bestDiff = diff;
                best = w;
            }
        }
        return best;
    }

    private EnemyWave findMatchingEnemyWave(String shooterName, Bullet b) {
        long now = getTime();
        EnemyWave best = null;
        double bestDiff = Double.POSITIVE_INFINITY;

        Point2D.Double myPos = new Point2D.Double(getX(), getY());

        for (EnemyWave w : enemyWaves) {
            if (!w.enemyName.equals(shooterName)) continue;
            if (Math.abs(w.bulletSpeed - b.getVelocity()) > 0.2) continue;

            double traveled = (now - w.fireTime) * w.bulletSpeed;
            double actual = w.fireLocation.distance(myPos);
            double diff = Math.abs(actual - traveled);

            if (diff < bestDiff) {
                bestDiff = diff;
                best = w;
            }
        }
        return best;
    }

    private void removeMyWaveForBullet(Bullet b) {
        long now = getTime();
        MyWave best = null;
        double bestDiff = Double.POSITIVE_INFINITY;

        Point2D.Double bulletPoint = new Point2D.Double(b.getX(), b.getY());

        for (MyWave w : myWaves) {
            if (Math.abs(w.bulletSpeed - b.getVelocity()) > 0.2) continue;

            double traveled = (now - w.fireTime) * w.bulletSpeed;
            double actual = w.fireLocation.distance(bulletPoint);
            double diff = Math.abs(actual - traveled);

            if (diff < bestDiff) {
                bestDiff = diff;
                best = w;
            }
        }
        if (best != null) myWaves.remove(best);
    }

    // ------------------ Utilitaires mouvement ------------------

    private void goTo(Point2D.Double p) {
        if (p == null) return;

        Point2D.Double myPos = new Point2D.Double(getX(), getY());
        double angle = absoluteBearing(myPos, p);
        double dist = myPos.distance(p);

        double turn = Utils.normalRelativeAngle(angle - getHeadingRadians());

        if (Math.abs(turn) > Math.PI / 2.0) {
            turn = Utils.normalRelativeAngle(turn + Math.PI);
            setTurnRightRadians(turn);
            setBack(dist);
        } else {
            setTurnRightRadians(turn);
            setAhead(dist);
        }

        double maxV = 8.0;
        if (Math.abs(getTurnRemaining()) > 40.0) maxV = 5.0;
        setMaxVelocity(maxV);
    }

    private void wander() {
        Point2D.Double center = new Point2D.Double(fieldW / 2.0, fieldH / 2.0);

        double angle = absoluteBearing(getX(), getY(), center.x, center.y)
                + orbitDirection * (Math.PI / 2.0);

        angle = wallSmooth(getX(), getY(), angle, orbitDirection);

        setMaxVelocity(8.0);
        setBackAsFront(angle, 120.0);
    }

    private void setBackAsFront(double goAngle, double distance) {
        double angle = Utils.normalRelativeAngle(goAngle - getHeadingRadians());

        if (Math.abs(angle) > Math.PI / 2.0) {
            if (angle < 0) setTurnRightRadians(Math.PI + angle);
            else setTurnLeftRadians(Math.PI - angle);
            setBack(distance);
        } else {
            if (angle < 0) setTurnLeftRadians(-angle);
            else setTurnRightRadians(angle);
            setAhead(distance);
        }
    }

    private double wallSmooth(double x, double y, double angle, int orientation) {
        double smooth = angle;
        for (int i = 0; i < 60; i++) {
            double testX = x + Math.sin(smooth) * WALL_STICK;
            double testY = y + Math.cos(smooth) * WALL_STICK;

            if (safeRect.contains(testX, testY)) {
                return smooth;
            }
            smooth += orientation * 0.05;
        }
        return smooth;
    }

    // ------------------ Segments gun ------------------

    private int getDistanceSeg(double d) {
        if (d < 150) return 0;
        if (d < 300) return 1;
        if (d < 450) return 2;
        if (d < 600) return 3;
        return 4;
    }

    private int getVelSeg(double v) {
        double av = Math.abs(v);
        if (av < 0.5) return 0;
        if (av < 2.5) return 1;
        if (av < 4.5) return 2;
        if (av < 6.5) return 3;
        return 4;
    }

    private int getWallSeg(double x, double y) {
        double m = Math.min(Math.min(x, fieldW - x), Math.min(y, fieldH - y));
        return m < 120 ? 1 : 0;
    }

    // ------------------ Stats helpers ------------------

    private void updateStats(double[] stats, int index, double weight) {
        for (int i = 0; i < stats.length; i++) {
            double d = i - index;
            stats[i] += weight / (d * d + 1.0);
        }
    }

    // ------------------ Math helpers ------------------

    private double absoluteBearing(double x1, double y1, double x2, double y2) {
        return Math.atan2(x2 - x1, y2 - y1);
    }

    private double absoluteBearing(Point2D.Double p1, Point2D.Double p2) {
        return Math.atan2(p2.x - p1.x, p2.y - p1.y);
    }

    private Point2D.Double project(Point2D.Double src, double angle, double length) {
        return new Point2D.Double(
                src.x + Math.sin(angle) * length,
                src.y + Math.cos(angle) * length
        );
    }

    private double distance(double x1, double y1, double x2, double y2) {
        return Math.hypot(x2 - x1, y2 - y1);
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
