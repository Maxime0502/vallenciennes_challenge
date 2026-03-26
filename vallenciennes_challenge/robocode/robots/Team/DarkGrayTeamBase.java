package Team;

import robocode.*;
import robocode.util.Utils;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public abstract class DarkGrayTeamBase extends TeamRobot {

    protected final Map<String, EnemyInfo> enemies = new HashMap<>();
    protected final Map<String, MateInfo> mates = new HashMap<>();

    protected EnemyInfo target;
    protected String focusTarget;

    protected double fieldW;
    protected double fieldH;
    protected Rectangle2D.Double safeRect;

    protected static final double BOT_RADIUS = 18.0;
    protected static final double WALL_MARGIN = 70.0;
    protected static final double WALL_STICK = 170.0;

    protected int orbitDirection = 1;
    protected long lastMateBroadcast = -100;
    protected long lastFocusBroadcast = -100;
    protected long lastDirectionFlip = -100;

    protected final Random rng = new Random();

    protected abstract boolean isLeaderBot();
    protected abstract double preferredDistance();
    protected abstract double roleOffsetAngle();
    protected abstract double fireCap();
    protected abstract double aggression();

    protected Color bodyColor() { return Color.DARK_GRAY; }
    protected Color gunColor() { return Color.BLACK; }
    protected Color radarColor() { return Color.GRAY; }
    protected Color bulletColor() { return Color.CYAN; }
    protected Color scanColor() { return Color.WHITE; }

    public void run() {
        fieldW = getBattleFieldWidth();
        fieldH = getBattleFieldHeight();

        safeRect = new Rectangle2D.Double(
                WALL_MARGIN, WALL_MARGIN,
                fieldW - 2.0 * WALL_MARGIN,
                fieldH - 2.0 * WALL_MARGIN
        );

        setBodyColor(bodyColor());
        setGunColor(gunColor());
        setRadarColor(radarColor());
        setBulletColor(bulletColor());
        setScanColor(scanColor());

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);

        setTurnRadarRightRadians(Double.POSITIVE_INFINITY);

        while (true) {
            broadcastMateStateIfNeeded();
            cleanupData();

            if (isLeaderBot() && (getTime() % 3 == 0 || focusTarget == null)) {
                selectAndBroadcastFocusTarget();
            }

            resolveTarget();
            doRadar();
            doMovement();
            doGun();

            execute();
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        if (isTeammate(e.getName())) {
            return;
        }

        EnemyInfo en = enemies.get(e.getName());
        if (en == null) {
            en = new EnemyInfo();
            en.name = e.getName();
            enemies.put(en.name, en);
        }

        double absBearing = getHeadingRadians() + e.getBearingRadians();
        double ex = getX() + Math.sin(absBearing) * e.getDistance();
        double ey = getY() + Math.cos(absBearing) * e.getDistance();

        double oldHeading = en.heading;

        en.x = ex;
        en.y = ey;
        en.energy = e.getEnergy();
        en.heading = e.getHeadingRadians();
        en.velocity = e.getVelocity();
        en.turnRate = Utils.normalRelativeAngle(en.heading - oldHeading);
        en.lastSeen = getTime();
        en.alive = true;

        try {
            broadcastMessage(new EnemyScanMessage(
                    en.name, en.x, en.y, en.energy, en.heading,
                    en.velocity, en.turnRate, en.lastSeen
            ));
        } catch (IOException ignored) {
        }

        if (isLeaderBot() && (focusTarget == null || shouldReplaceFocus(en))) {
            focusTarget = en.name;
            broadcastFocus(en);
        }
    }

    public void onMessageReceived(MessageEvent e) {
        Object msg = e.getMessage();

        if (msg instanceof EnemyScanMessage m) {
            EnemyInfo en = enemies.get(m.name);
            if (en == null) {
                en = new EnemyInfo();
                en.name = m.name;
                enemies.put(en.name, en);
            }

            if (m.time >= en.lastSeen) {
                en.x = m.x;
                en.y = m.y;
                en.energy = m.energy;
                en.heading = m.heading;
                en.velocity = m.velocity;
                en.turnRate = m.turnRate;
                en.lastSeen = m.time;
                en.alive = true;
            }
        } else if (msg instanceof MateStateMessage m) {
            if (m.name.equals(getName())) return;

            MateInfo mate = mates.get(m.name);
            if (mate == null) {
                mate = new MateInfo();
                mate.name = m.name;
                mates.put(mate.name, mate);
            }

            mate.x = m.x;
            mate.y = m.y;
            mate.energy = m.energy;
            mate.heading = m.heading;
            mate.lastSeen = m.time;
            mate.alive = true;
        } else if (msg instanceof FocusMessage m) {
            focusTarget = m.targetName;

            EnemyInfo en = enemies.get(m.targetName);
            if (en == null) {
                en = new EnemyInfo();
                en.name = m.targetName;
                enemies.put(en.name, en);
            }

            if (m.time >= en.lastSeen) {
                en.x = m.x;
                en.y = m.y;
                en.energy = m.energy;
                en.lastSeen = m.time;
                en.alive = true;
            }
        }
    }

    public void onRobotDeath(RobotDeathEvent e) {
        String dead = e.getName();

        if (isTeammate(dead)) {
            MateInfo mate = mates.get(dead);
            if (mate != null) mate.alive = false;
        } else {
            EnemyInfo en = enemies.get(dead);
            if (en != null) en.alive = false;
            if (focusTarget != null && focusTarget.equals(dead)) {
                focusTarget = null;
            }
        }

        if (target != null && target.name.equals(dead)) {
            target = null;
        }
    }

    public void onHitByBullet(HitByBulletEvent e) {
        orbitDirection = -orbitDirection;
        lastDirectionFlip = getTime();
    }

    public void onHitWall(HitWallEvent e) {
        orbitDirection = -orbitDirection;
        lastDirectionFlip = getTime();
        setBack(80);
    }

    public void onHitRobot(HitRobotEvent e) {
        if (!isTeammate(e.getName())) {
            orbitDirection = -orbitDirection;
            lastDirectionFlip = getTime();

            EnemyInfo en = enemies.get(e.getName());
            if (en != null && en.energy < 10 && getEnergy() > en.energy + 10) {
                setAhead(60);
            } else {
                setBack(70);
            }
        }
    }

    protected void broadcastMateStateIfNeeded() {
        if (getTime() - lastMateBroadcast < 2) return;
        lastMateBroadcast = getTime();

        try {
            broadcastMessage(new MateStateMessage(
                    getName(), getX(), getY(), getEnergy(), getHeadingRadians(), getTime()
            ));
        } catch (IOException ignored) {
        }
    }

    protected void cleanupData() {
        for (EnemyInfo en : enemies.values()) {
            if (en.alive && getTime() - en.lastSeen > 60) {
                en.alive = false;
            }
        }

        for (MateInfo mate : mates.values()) {
            if (mate.alive && getTime() - mate.lastSeen > 20) {
                mate.alive = false;
            }
        }
    }

    protected void selectAndBroadcastFocusTarget() {
        EnemyInfo best = chooseBestTarget();
        if (best != null) {
            focusTarget = best.name;
            broadcastFocus(best);
        }
    }

    protected void broadcastFocus(EnemyInfo en) {
        if (getTime() - lastFocusBroadcast < 2) return;
        lastFocusBroadcast = getTime();

        try {
            broadcastMessage(new FocusMessage(en.name, en.x, en.y, en.energy, getTime()));
        } catch (IOException ignored) {
        }
    }

    protected boolean shouldReplaceFocus(EnemyInfo candidate) {
        EnemyInfo current = enemies.get(focusTarget);
        if (current == null || !current.alive) return true;
        return scoreTarget(candidate) > scoreTarget(current) + 0.8;
    }

    protected void resolveTarget() {
        EnemyInfo focus = enemies.get(focusTarget);
        if (focus != null && focus.alive && getTime() - focus.lastSeen <= 20) {
            target = focus;
            return;
        }

        target = chooseBestTarget();
    }

    protected EnemyInfo chooseBestTarget() {
        EnemyInfo best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (EnemyInfo en : enemies.values()) {
            if (!en.alive) continue;
            if (getTime() - en.lastSeen > 35) continue;

            double s = scoreTarget(en);
            if (s > bestScore) {
                bestScore = s;
                best = en;
            }
        }
        return best;
    }

    protected double scoreTarget(EnemyInfo en) {
        double dist = distance(getX(), getY(), en.x, en.y);

        double score = 0.0;
        score += 1800.0 / (dist + 100.0);
        score += (100.0 - Math.min(100.0, en.energy)) * 0.08;
        score += Math.max(0.0, 20.0 - (getTime() - en.lastSeen)) * 0.08;

        if (focusTarget != null && focusTarget.equals(en.name)) score += 1.5;
        if (en.energy < 20) score += 1.0;
        if (en.energy < 8) score += 1.2;
        if (dist < 180) score += 0.8;

        if (friendlyFireRisk(new Point2D.Double(en.x, en.y))) score -= 2.0;

        return score;
    }

    protected void doRadar() {
        if (target != null && target.alive && getTime() - target.lastSeen <= 8) {
            double absBearing = absoluteBearing(getX(), getY(), target.x, target.y);
            double turn = Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians());
            double dist = distance(getX(), getY(), target.x, target.y);
            double extra = Math.atan(36.0 / Math.max(1.0, dist));
            turn += Math.signum(turn == 0 ? 1 : turn) * extra;
            setTurnRadarRightRadians(turn);
        } else {
            setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
        }
    }

    protected void doGun() {
        if (target == null || !target.alive) return;
        if (getTime() - target.lastSeen > 20) return;

        Point2D.Double myPos = new Point2D.Double(getX(), getY());
        double dist = myPos.distance(target.x, target.y);

        double power = computeFirePower(dist, target.energy);
        double bulletSpeed = Rules.getBulletSpeed(power);

        Point2D.Double aimPoint = predictTargetPosition(target, bulletSpeed);
        if (friendlyFireRisk(aimPoint)) {
            return;
        }

        double aimAngle = absoluteBearing(myPos, aimPoint);
        double gunTurn = Utils.normalRelativeAngle(aimAngle - getGunHeadingRadians());
        setTurnGunRightRadians(gunTurn);

        double tolerance = Math.toRadians(dist < 160 ? 6.5 : dist < 320 ? 4.0 : 2.2);

        if (Math.abs(getGunTurnRemainingRadians()) < tolerance
                && getGunHeat() == 0.0
                && getEnergy() > power + 0.15) {
            setFire(power);
        }
    }

    protected double computeFirePower(double distance, double enemyEnergy) {
        double p = 1.1 + aggression() * 1.1;

        if (distance < 140) p += 0.9;
        else if (distance < 240) p += 0.45;
        else if (distance > 500) p -= 0.45;

        if (enemyEnergy < 20) p += 0.25;
        if (enemyEnergy < 8) p += 0.20;

        if (getOthers() > 6) p = Math.min(p, 2.2);
        if (getEnergy() < 25) p = Math.min(p, 1.8);
        if (getEnergy() < 12) p = Math.min(p, 1.2);
        if (getEnergy() < 6) p = Math.min(p, 0.8);

        p = Math.min(p, fireCap());
        return clamp(p, 0.3, 3.0);
    }

    protected Point2D.Double predictTargetPosition(EnemyInfo en, double bulletSpeed) {
        Point2D.Double p = new Point2D.Double(en.x, en.y);
        double heading = en.heading;
        double velocity = en.velocity;

        for (int i = 0; i < 80; i++) {
            if (distance(getX(), getY(), p.x, p.y) <= (i + 1) * bulletSpeed) {
                break;
            }

            heading += en.turnRate;
            p.x += Math.sin(heading) * velocity;
            p.y += Math.cos(heading) * velocity;

            p.x = clamp(p.x, BOT_RADIUS, fieldW - BOT_RADIUS);
            p.y = clamp(p.y, BOT_RADIUS, fieldH - BOT_RADIUS);
        }

        return p;
    }

    protected void doMovement() {
        if (target == null || !target.alive || getTime() - target.lastSeen > 30) {
            patrol();
            return;
        }

        if (getTime() - lastDirectionFlip > 28) {
            if (rng.nextDouble() < 0.035) {
                orbitDirection = -orbitDirection;
                lastDirectionFlip = getTime();
            }
        }

        Point2D.Double myPos = new Point2D.Double(getX(), getY());
        Point2D.Double enemyPos = new Point2D.Double(target.x, target.y);

        double dist = myPos.distance(enemyPos);
        double absBearing = absoluteBearing(myPos, enemyPos);

        double distanceError = dist - preferredDistance();
        double approach = clamp(distanceError / 240.0, -0.95, 0.95);

        double moveAngle = absBearing + orbitDirection * (Math.PI / 2.0 - approach * 0.62) + roleOffsetAngle() * 0.35;

        double vx = Math.sin(moveAngle);
        double vy = Math.cos(moveAngle);

        for (MateInfo mate : mates.values()) {
            if (!mate.alive) continue;
            if (getTime() - mate.lastSeen > 8) continue;

            double d = distance(getX(), getY(), mate.x, mate.y);
            if (d < 170 && d > 0.1) {
                double force = (170.0 - d) / 170.0;
                vx += ((getX() - mate.x) / d) * 1.3 * force;
                vy += ((getY() - mate.y) / d) * 1.3 * force;
            }
        }

        double edgeX = 0.0;
        double edgeY = 0.0;

        if (getX() < WALL_MARGIN + 50) edgeX += 1.0;
        if (getX() > fieldW - WALL_MARGIN - 50) edgeX -= 1.0;
        if (getY() < WALL_MARGIN + 50) edgeY += 1.0;
        if (getY() > fieldH - WALL_MARGIN - 50) edgeY -= 1.0;

        vx += edgeX * 0.8;
        vy += edgeY * 0.8;

        double finalAngle = Math.atan2(vx, vy);
        finalAngle = wallSmooth(getX(), getY(), finalAngle, orbitDirection);

        double moveDistance = 135.0 + aggression() * 40.0;

        if (dist > preferredDistance() + 180) moveDistance += 35.0;
        if (dist < preferredDistance() - 80) moveDistance -= 20.0;

        setMaxVelocity(Math.abs(getTurnRemaining()) > 35 ? 5.2 : 8.0);
        setBackAsFront(finalAngle, moveDistance);
    }

    protected void patrol() {
        Point2D.Double center = new Point2D.Double(fieldW / 2.0, fieldH / 2.0);
        double angle = (getTime() / 35.0) + roleOffsetAngle();
        Point2D.Double p = project(center, angle, 180.0 + aggression() * 60.0);

        if (!safeRect.contains(p)) {
            p = new Point2D.Double(fieldW / 2.0, fieldH / 2.0);
        }

        goTo(p);
    }

    protected boolean friendlyFireRisk(Point2D.Double targetPoint) {
        Point2D.Double from = new Point2D.Double(getX(), getY());
        double targetDist = from.distance(targetPoint);

        for (MateInfo mate : mates.values()) {
            if (!mate.alive) continue;
            if (getTime() - mate.lastSeen > 8) continue;

            Point2D.Double mp = new Point2D.Double(mate.x, mate.y);
            double mateDist = from.distance(mp);

            if (mateDist >= targetDist) continue;

            double segDist = pointToSegmentDistance(mp, from, targetPoint);
            if (segDist < 42.0) {
                return true;
            }
        }

        return false;
    }

    protected void goTo(Point2D.Double p) {
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

        setMaxVelocity(Math.abs(getTurnRemaining()) > 35 ? 5.0 : 8.0);
    }

    protected void setBackAsFront(double goAngle, double distance) {
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

    protected double wallSmooth(double x, double y, double angle, int orientation) {
        double smooth = angle;

        for (int i = 0; i < 80; i++) {
            double testX = x + Math.sin(smooth) * WALL_STICK;
            double testY = y + Math.cos(smooth) * WALL_STICK;

            if (safeRect.contains(testX, testY)) {
                return smooth;
            }

            smooth += orientation * 0.05;
        }

        return smooth;
    }

    protected double pointToSegmentDistance(Point2D.Double p, Point2D.Double a, Point2D.Double b) {
        double abx = b.x - a.x;
        double aby = b.y - a.y;
        double apx = p.x - a.x;
        double apy = p.y - a.y;

        double ab2 = abx * abx + aby * aby;
        if (ab2 == 0) return p.distance(a);

        double t = (apx * abx + apy * aby) / ab2;
        t = clamp(t, 0.0, 1.0);

        double cx = a.x + abx * t;
        double cy = a.y + aby * t;

        return Point2D.distance(p.x, p.y, cx, cy);
    }

    protected double absoluteBearing(double x1, double y1, double x2, double y2) {
        return Math.atan2(x2 - x1, y2 - y1);
    }

    protected double absoluteBearing(Point2D.Double p1, Point2D.Double p2) {
        return Math.atan2(p2.x - p1.x, p2.y - p1.y);
    }

    protected Point2D.Double project(Point2D.Double src, double angle, double length) {
        return new Point2D.Double(
                src.x + Math.sin(angle) * length,
                src.y + Math.cos(angle) * length
        );
    }

    protected double distance(double x1, double y1, double x2, double y2) {
        return Math.hypot(x2 - x1, y2 - y1);
    }

    protected double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}