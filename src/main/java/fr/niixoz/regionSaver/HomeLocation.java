package fr.niixoz.regionSaver;

public class HomeLocation {

    private String worldName;
    private String homeName;
    private float x;
    private float z;

    public HomeLocation(String homeName, String worldName, float x, float z) {
        this.homeName = homeName;
        this.worldName = worldName;
        this.x = x;
        this.z = z;
    }

    public String getHomeName() {
        return this.homeName;
    }

    public String getWorldName() {
        return this.worldName;
    }

    public float getX() {
        return this.x;
    }

    public float getZ() {
        return this.z;
    }

}
