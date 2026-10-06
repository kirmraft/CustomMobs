package ca.pandaaa.custommobs.utils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

public class Utils {
    public static String applyFormat(String message) {
        message = message.replace(">>", "»").replace("<<", "«");

        Pattern hexPattern = Pattern.compile("&#([A-Fa-f0-9]){6}");
        Matcher matcherHex = hexPattern.matcher(message);
        while (matcherHex.find()) {
            ChatColor hexColor = ChatColor.of(matcherHex.group().substring(1));
            String before = message.substring(0, matcherHex.start());
            String after = message.substring(matcherHex.end());
            message = before + hexColor + after;
            matcherHex = hexPattern.matcher(message);
        }

        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static ItemStack createHead(String url) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);

        if (url != null && !url.isEmpty()) {
            SkullMeta headMeta = (SkullMeta) head.getItemMeta();
            headMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            try {
                PlayerProfile playerProfile = Bukkit.createPlayerProfile(UUID.randomUUID(), "CustomHead");

                PlayerTextures playerTextures = playerProfile.getTextures();
                playerTextures.setSkin(new URL("https://textures.minecraft.net/texture/" + url));
                playerProfile.setTextures(playerTextures);

                headMeta.setOwnerProfile(playerProfile);
                head.setItemMeta(headMeta);
            } catch (MalformedURLException exception) {
                exception.printStackTrace();
            }
        }

        return head;
    }

    public static String getSentenceCase(String string) {
        return string.toUpperCase().substring(0, 1) + string.toLowerCase().substring(1).replaceAll("_", " ");
    }

    public static String getStartCase(String string) {
        String[] words = string.replaceAll("\\.", " ").toLowerCase().split("[_\\s]+");
        StringBuilder startCaseString = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                startCaseString.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }

        return startCaseString.toString().trim();
    }

    public static ChatColor getChatColorOfColor(String color) {
        ChatColor chatColor;
        switch (color.toUpperCase()) {
            case "RED":
                chatColor = ChatColor.RED;
                break;
            case "BLUE":
                chatColor = ChatColor.BLUE;
                break;
            case "GREEN": case "LIME":
                chatColor = ChatColor.GREEN;
                break;
            case "YELLOW":
                chatColor = ChatColor.YELLOW;
                break;
            case "BLACK": case "DARK_BROWN":
                chatColor = ChatColor.DARK_GRAY;
                break;
            case "ORANGE": case "CHESTNUT": case "CREAMY":
                chatColor = ChatColor.GOLD;
                break;
            case "PINK": case "MAGENTA":
                chatColor = ChatColor.LIGHT_PURPLE;
                break;
            case "CYAN":
                chatColor = ChatColor.DARK_AQUA;
                break;
            case "GRAY": case "LIGHT_GRAY":
                chatColor = ChatColor.GRAY;
                break;
            case "LIGHT_BLUE":
                chatColor = ChatColor.AQUA;
                break;
            case "PURPLE":
                chatColor = ChatColor.DARK_PURPLE;
                break;
            case "BROWN":
                chatColor = ChatColor.DARK_RED;
                break;
            case "WHITE": default:
                chatColor = ChatColor.WHITE;
                break;
        }

        return chatColor;
    }

    public static String getFormattedTime(int timeInSecond, boolean small, boolean showInfinite) {
        String hoursString = small ? " h" : " hour";
        String minutesString = small ? " m" : " minute";
        String secondsString = small ? " s" : " second";

        if(timeInSecond <= 0) {
            if(showInfinite)
                return "Infinite";
            else
                return "0";
        }
        String formattedSize = "";

        boolean hours = false;
        if (timeInSecond >= 3600) {
            formattedSize += timeInSecond / 3600 + hoursString + (timeInSecond >= 7200 && !small ? "s" : "");
            hours = true;
        }
        timeInSecond = timeInSecond % 3600;

        boolean minutes = false;
        if (timeInSecond >= 60) {
            formattedSize += (hours ? " & " : "") + timeInSecond / 60 + minutesString + (timeInSecond >= 120 && !small ? "s" : "");
            minutes = true;
        }
        timeInSecond = timeInSecond % 60;

        if (timeInSecond > 0)
            formattedSize += (minutes || hours && !minutes ? " & " : "") + timeInSecond + secondsString + (timeInSecond > 1 && !small ? "s" : "");

        return formattedSize;
    }

    public static boolean isVersionAtLeast(String targetVersion) {
        return compareVersions(Bukkit.getBukkitVersion(), targetVersion) >= 0;
    }

    public static boolean isVersionBeforeOrEqual(String targetVersion) {
        return compareVersions(Bukkit.getBukkitVersion(), targetVersion) <= 0;
    }

    /**
     * Compares the game version of a Bukkit version string with a target like "26.2".
     * Only the leading numeric parts count: Spigot "26.2-R0.1-SNAPSHOT" and Paper "26.2.build.92-stable" are both 26.2.
     */
    static int compareVersions(String bukkitVersion, String targetVersion) {
        int[] current = leadingNumbers(bukkitVersion.split("-")[0]);
        int[] target = leadingNumbers(targetVersion);
        for (int i = 0; i < Math.max(current.length, target.length); i++) {
            int difference = (i < current.length ? current[i] : 0) - (i < target.length ? target[i] : 0);
            if (difference != 0)
                return difference;
        }
        return 0;
    }

    private static int[] leadingNumbers(String version) {
        return java.util.Arrays.stream(version.split("\\."))
                .takeWhile(part -> !part.isEmpty() && part.chars().allMatch(Character::isDigit))
                .mapToInt(Integer::parseInt)
                .toArray();
    }

    public static boolean isVersionExactly(String version) {
        return isVersionAtLeast(version) && isVersionBeforeOrEqual(version);
    }
}