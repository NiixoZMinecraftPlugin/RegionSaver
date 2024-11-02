package fr.niixoz.regionSaver.permissions;

public enum PermissionEnum {

    PERMISSION_ALL("regionsaver.*"),
    ADMIN_REGION_SAVE("regionsaver.admin.save"),

    ;

    private String permission;

    PermissionEnum(String permission) {
        this.permission = permission;
    }

    public String getPermission() {
        return permission;
    }
}
