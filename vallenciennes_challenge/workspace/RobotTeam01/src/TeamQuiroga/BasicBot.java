package TeamQuiroga;


import robocode.*;

public class BasicBot extends Robot {

	// ----------------------
    // Main method
    // ----------------------
    @Override
    public void run() {
        // This is your main loop
        while (true) {
            // Here you can move, turn the gun, fire, etc.
        }
    }

    // ----------------------
    // Main events
    // ----------------------

    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        // Called when another robot is scanned
    }

    @Override
    public void onHitByBullet(HitByBulletEvent e) {
        // Called when hit by a bullet
    }

    @Override
    public void onHitWall(HitWallEvent e) {
        // Called when the robot hits a wall
    }

    @Override
    public void onHitRobot(HitRobotEvent e) {
        // Called when the robot collides with another robot
    }

    @Override
    public void onBulletHit(BulletHitEvent e) {
        // Called when a bullet hits an enemy
    }

    @Override
    public void onBulletMissed(BulletMissedEvent e) {
        // Called when a bullet misses
    }

    @Override
    public void onDeath(DeathEvent e) {
        // Called when the robot dies
    }

    @Override
    public void onWin(WinEvent e) {
        // Called when the robot wins the battle
    }
}
