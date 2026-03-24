package Noel;

import java.awt.Color;
import robocode.*;
import robocode.util.Utils;

public class FlankBot extends TeamRobot {

    private int direction = 1;
    private String targetName = null;

    public void run() {
        setBodyColor(Color.GRAY);
        setGunColor(Color.GRAY);
        setRadarColor(Color.GRAY);
        setBulletColor(Color.GRAY);
        setScanColor(Color.GRAY);

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        while (true) {
            setAhead(250 * direction);
            setTurnRight(60 * direction);
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

        double absBearing = getHeadingRadians() + e.getBearingRadians();

        double radarTurn = Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians());
        setTurnRadarRightRadians(radarTurn * 2);

        if (targetName == null || e.getName().equals(targetName)) {
            setTurnRightRadians(Utils.normalRelativeAngle(e.getBearingRadians() + Math.PI / 2 - 0.7 * direction));
            setAhead(180 * direction);

            if (Math.random() < 0.15) {
                direction = -direction;
            }

            double gunTurn = Utils.normalRelativeAngle(absBearing - getGunHeadingRadians());
            setTurnGunRightRadians(gunTurn);

            double firePower;
            if (e.getDistance() < 100) {
                firePower = 2.5;
            } else if (e.getDistance() < 250) {
                firePower = 1.8;
            } else {
                firePower = 1.0;
            }

            if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 12) {
                setFire(firePower);
            }
        } else {
            setTurnRightRadians(Utils.normalRelativeAngle(e.getBearingRadians() + Math.PI / 2));
            setAhead(120 * direction);
        }

        execute();
    }

    public void onHitWall(HitWallEvent e) {
        direction = -direction;
        setBack(120);
        setTurnRight(90);
        execute();
    }

    public void onHitByBullet(HitByBulletEvent e) {
        direction = -direction;
        setTurnRight(70);
        setAhead(150);
        execute();
    }

    public void onHitRobot(HitRobotEvent e) {
        if (isTeammate(e.getName())) {
            setBack(80);
            setTurnRight(50);
            execute();
            return;
        }

        setFire(2.5);

        if (e.isMyFault()) {
            setBack(60);
        }

        execute();
    }
}