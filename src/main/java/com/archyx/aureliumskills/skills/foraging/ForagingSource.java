package com.archyx.aureliumskills.skills.foraging;

import com.archyx.aureliumskills.skills.Skill;
import com.archyx.aureliumskills.skills.Skills;
import com.archyx.aureliumskills.source.Source;
import com.cryptomorin.xseries.XMaterial;
import org.bukkit.block.Block;
import org.jetbrains.annotations.Nullable;

public enum ForagingSource implements Source {

    OAK_LOG("LOG", new byte[] {0, 4, 8, 12}, "OAK_WOOD"),
    SPRUCE_LOG("LOG", new byte[] {1, 5, 9, 13}, "SPRUCE_WOOD"),
    BIRCH_LOG("LOG", new byte[] {2, 6, 10, 14}, "BIRCH_WOOD"),
    JUNGLE_LOG("LOG", new byte[] {3, 7, 11, 15}, "JUNGLE_WOOD"),
    ACACIA_LOG("LOG_2", new byte[] {0, 4, 8, 12}, "ACACIA_WOOD"),
    DARK_OAK_LOG("LOG_2", new byte[] {1, 5, 9, 13}, "DARK_OAK_WOOD"),
    OAK_LEAVES("LEAVES", new byte[] {0, 8}),
    SPRUCE_LEAVES("LEAVES", new byte[] {1, 9}),
    BIRCH_LEAVES("LEAVES", new byte[] {2, 10}),
    JUNGLE_LEAVES("LEAVES", new byte[] {3, 11}),
    ACACIA_LEAVES("LEAVES_2", new byte[] {0, 8}),
    DARK_OAK_LEAVES("LEAVES_2", new byte[] {1, 9}),
    CRIMSON_STEM(new String[] {"CRIMSON_HYPHAE"}),
    WARPED_STEM(new String[] {"WARPED_HYPHAE"}),
    NETHER_WART_BLOCK,
    WARPED_WART_BLOCK,
    MOSS_BLOCK,
    MOSS_CARPET(true),
    AZALEA(true),
    FLOWERING_AZALEA(true),
    AZALEA_LEAVES,
    FLOWERING_AZALEA_LEAVES;

    private String[] alternateMaterials;
    private String legacyMaterial;
    private byte[] legacyData;
    private boolean requiresBlockBelow;

    ForagingSource() {

    }

    ForagingSource(boolean requiresBlockBelow) {
        this.requiresBlockBelow = requiresBlockBelow;
    }

    ForagingSource(String legacyMaterial) {
        this.legacyMaterial = legacyMaterial;
    }

    ForagingSource(String[] alternateMaterials) {
        this.alternateMaterials = alternateMaterials;
    }

    ForagingSource(String legacyMaterial, byte[] legacyData) {
        this(legacyMaterial);
        this.legacyData = legacyData;
    }

    ForagingSource(String legacyMaterial, byte[] legacyData, String... alternateMaterials) {
        this(legacyMaterial, legacyData);
        this.alternateMaterials = alternateMaterials;
    }

    @Nullable
    public String getLegacyMaterial() {
        return legacyMaterial;
    }

    public byte[] getLegacyData() {
        return legacyData;
    }

    public boolean requiresBlockBelow() {
        return requiresBlockBelow;
    }

    @Nullable
    public String[] getAlternateMaterials() {
        return alternateMaterials;
    }

    @SuppressWarnings("deprecation")
    public boolean isMatch(Block block) {
        boolean matched = false;
        String materialName = block.getType().toString();
        if (XMaterial.isNewVersion() || getLegacyMaterial() == null) { // Standard block handling
            if (toString().equalsIgnoreCase(materialName)) {
                matched = true;
            } else if (getAlternateMaterials() != null) {
                for (String alternate : getAlternateMaterials()) {
                    if (alternate.equalsIgnoreCase(materialName)) {
                        matched = true;
                        break;
                    }
                }
            }
        } else { // Legacy block handling
            if (getLegacyData() == null) { // No data value
                if (getLegacyMaterial().equalsIgnoreCase(materialName)) {
                    matched = true;
                }
            } else { // With data value
                if (getLegacyMaterial().equalsIgnoreCase(materialName) && byteArrayContains(legacyData, block.getData())) {
                    matched = true;
                }
            }
        }
        return matched;
    }

    private boolean byteArrayContains(byte[] array, byte input) {
        for (byte b : array) {
            if (b == input) return true;
        }
        return false;
    }

    @Override
    public Skill getSkill() {
        return Skills.FORAGING;
    }

    @Nullable
    public static ForagingSource getSource(Block block) {
        for (ForagingSource source : values()) {
            if (source.isMatch(block)) {
                return source;
            }
        }
        return null;
    }
}
