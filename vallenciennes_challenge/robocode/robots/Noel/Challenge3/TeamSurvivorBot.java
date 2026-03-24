package Noel;

import java.awt.Color;
import robocode.*;
import robocode.util.Utils;

public class TeamSurvivorBot extends TeamRobot {

    private int direction = 1;
    private boolean fleeMode = false;
    private String targetName = null;

    public void run() {
        setBodyColor(Color.DARK_GRAY);
        setGunColor(Color.DARK_GRAY);
        setRadarColor(Color.DARK_GRAY);
        setBulletColor(Color.DARK_GRAY);
        setScanColor(Color.DARK_GRAY);

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        while (true) {
            fleeMode = getEnergy() < 20;

            if (fleeMode) {
                setAhead(400 * direction);
                setTurnRight(140 * direction);
            }

            setTurnRadarRight(360);
            execute();
        }
    }

    public void onMessageReceived(MessageEvent e) {
        Object msg = e.getMessage();
        if (msg instanceof String) {
            targetName = (String) msg;
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        if (isTeammate(e.getName())) return;

        fleeMode = getEnergy() < 20;

        double absBearing = getHeadingRadians() + e.getBearingRadians();

        if (fleeMode) {
            setTurnRightRadians(Utils.normalRelativeAngle(e.getBearingRadians() + Math.PI / 2 - 0.9 * direction));
            setAhead(240 * direction);

            if (Math.random() < 0.20) {
                direction = -direction;
            }
        } else {
            setTurnRightRadians(Utils.normalRelativeAngle(e.getBearingRadians() + Math.PI / 2 - 0.25 * direction));
            setAhead(100 * direction);

            if (Math.random() < 0.05) {
                direction = -direction;
            }
        }

        double radarTurn = Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians());
        setTurnRadarRightRadians(radarTurn * 1.5);

        if (targetName == null || e.getName().equals(targetName) || e.getDistance() < 180) {
            double gunTurn = Utils.normalRelativeAngle(absBearing - getGunHeadingRadians());
            setTurnGunRightRadians(gunTurn);

            double firePower;
            if (fleeMode) {
                firePower = 0.8;
            } else if (e.getDistance() < 120) {
                firePower = 2.2;
            } else if (e.getDistance() < 300) {
                firePower = 1.5;
            } else {
                firePower = 1.0;
            }

            if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 10) {
                setFire(firePower);
            }
        }

        execute();
    }

    public void onHitWall(HitWallEvent e) {
        direction = -direction;
        setBack(160);
        setTurnRight(100);
        execute();
    }

    public void onHitByBullet(HitByBulletEvent e) {
        direction = -direction;
        setTurnRight(90 - e.getBearing());
        setAhead(160);
        execute();
    }

    public void onHitRobot(HitRobotEvent e) {
        if (isTeammate(e.getName())) {
            setBack(100);
            setTurnRight(60);
            execute();
            return;
        }

        if (!fleeMode) {
            setFire(2);
        }

        if (e.isMyFault()) {
            setBack(100);
        }

        execute();
    }
}