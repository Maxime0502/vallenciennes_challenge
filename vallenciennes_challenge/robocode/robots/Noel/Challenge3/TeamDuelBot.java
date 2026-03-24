package Noel;

import java.awt.Color;

import robocode.*;
import robocode.util.Utils;

public class TeamDuelBot extends TeamRobot {

    private int direction = 1;
    private String teamTarget = null;

    public void run() {
        setBodyColor(Color.WHITE);
        setGunColor(Color.WHITE);
        setRadarColor(Color.WHITE);
        setBulletColor(Color.WHITE);
        setScanColor(Color.WHITE);

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        while (true) {
            setAhead(4000);
            setTurnRadarRight(360);
            execute();
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        if (isTeammate(e.getName())) {
            return;
        }

        teamTarget = e.getName();

        try {
            broadcastMessage(teamTarget);
        } catch (Exception ex) {
        }

        double absBearing = getHeadingRadians() + e.getBearingRadians();

        double radarTurn = Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians());
        setTurnRadarRightRadians(radarTurn * 2);

        setTurnRightRadians(Utils.normalRelativeAngle(e.getBearingRadians() + Math.PI / 2 - 0.35 * direction));
        setAhead(120 * direction);

        if (Math.random() < 0.07) {
            direction = -direction;
        }

        double enemyX = getX() + Math.sin(absBearing) * e.getDistance();
        double enemyY = getY() + Math.cos(absBearing) * e.getDistance();

        double bulletPower;
        if (e.getDistance() < 120) {
            bulletPower = 3.0;
        } else if (e.getDistance() < 300) {
            bulletPower = 2.0;
        } else {
            bulletPower = 1.2;
        }

        double bulletSpeed = 20 - 3 * bulletPower;
        double enemyHeading = e.getHeadingRadians();
        double enemyVelocity = e.getVelocity();

        double predictedX = enemyX;
        double predictedY = enemyY;
        double time = 0;

        while ((++time) * bulletSpeed < Math.hypot(predictedX - getX(), predictedY - getY())) {
            predictedX += Math.sin(enemyHeading) * enemyVelocity;
            predictedY += Math.cos(enemyHeading) * enemyVelocity;

            predictedX = Math.max(18, Math.min(getBattleFieldWidth() - 18, predictedX));
            predictedY = Math.max(18, Math.min(getBattleFieldHeight() - 18, predictedY));
        }

        double aim = Math.atan2(predictedX - getX(), predictedY - getY());
        double gunTurn = Utils.normalRelativeAngle(aim - getGunHeadingRadians());
        setTurnGunRightRadians(gunTurn);

        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 8) {
            setFire(bulletPower);
        }

        execute();
    }

    public void onMessageReceived(MessageEvent e) {
        Object msg = e.getMessage();
        if (msg instanceof String) {
            teamTarget = (String) msg;
        }
    }

    public void onHitWall(HitWallEvent e) {
        direction = -direction;
        setBack(100);
        setTurnRight(80);
        execute();
    }

    public void onHitByBullet(HitByBulletEvent e) {
        direction = -direction;
        setTurnRight(45);
        setAhead(90);
        execute();
    }

    public void onHitRobot(HitRobotEvent e) {
        if (isTeammate(e.getName())) {
            setBack(80);
            setTurnRight(60);
            execute();
            return;
        }

        setFire(3);
        if (e.isMyFault()) {
            setBack(60);
        }
        execute();
    }
}