//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package Raph;

import java.awt.Color;
import robocode.AdvancedRobot;
import robocode.HitRobotEvent;
import robocode.HitWallEvent;
import robocode.ScannedRobotEvent;
import robocode.WinEvent;
import robocode.util.Utils;

public class GreyDarkChallenge1 extends AdvancedRobot {
    private double moveDirection = (double)1.0F;
    private double lastScanTime = (double)0.0F;

    public void run() {
        Color var1 = new Color(35, 35, 35);
        this.setBodyColor(var1);
        this.setGunColor(var1);
        this.setRadarColor(var1);
        this.setBulletColor(Color.LIGHT_GRAY);
        this.setScanColor(new Color(50, 50, 50));
        this.setAdjustRadarForGunTurn(true);
        this.setAdjustGunForRobotTurn(true);

        while(true) {
            this.turnRadarRightRadians(Double.POSITIVE_INFINITY);
        }
    }

    public void onScannedRobot(ScannedRobotEvent var1) {
        double var2 = this.getHeadingRadians() + var1.getBearingRadians();
        double var4 = var1.getDistance();
        double var6 = var1.getBearingRadians() + (Math.PI / 2D) + (var4 < (double)400.0F ? 0.35 : 0.15);
        this.setTurnRightRadians(Utils.normalRelativeAngle(var6));
        if (this.getTime() % 20L == 0L) {
            this.moveDirection *= (double)(Math.random() > (double)0.5F ? 1 : -1);
        }

        this.setAhead((double)150.0F * this.moveDirection);
        if (var4 < (double)500.0F || this.getEnergy() > (double)60.0F) {
            double var8 = Math.min(Math.min((double)3.0F, (double)600.0F / var4), var1.getEnergy() / (double)4.0F);
            double var10 = (double)20.0F - (double)3.0F * var8;
            double var12 = var1.getVelocity() * Math.sin(var1.getHeadingRadians() - var2) / var10;
            this.setTurnGunRightRadians(Utils.normalRelativeAngle(var2 - this.getGunHeadingRadians() + var12));
            if (this.getGunHeat() == (double)0.0F && Math.abs(this.getGunTurnRemaining()) < (double)5.0F) {
                this.setFire(var8);
            }
        }

        double var14 = Utils.normalRelativeAngle(var2 - this.getRadarHeadingRadians());
        this.setTurnRadarRightRadians(var14 * (double)1.5F);
    }

    public void onHitWall(HitWallEvent var1) {
        this.moveDirection = -this.moveDirection;
        this.setAhead((double)150.0F * this.moveDirection);
    }

    public void onHitRobot(HitRobotEvent var1) {
        this.moveDirection = -this.moveDirection;
        this.setAhead((double)100.0F * this.moveDirection);
    }

    public void onWin(WinEvent var1) {
        for(int var2 = 0; var2 < 50; ++var2) {
            this.turnRight((double)30.0F);
            this.turnLeft((double)30.0F);
        }

    }
}