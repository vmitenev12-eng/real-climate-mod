package com.realclimate.block;

import net.minecraft.world.level.block.Block;

/**
 * Сплошной блок вентиляционной шахты: полный куб без щелей и без отверстий.
 * Нужен, чтобы прокладывать вентиляционные магистрали внутри построек из
 * миниблоков (LittleTiles): воздуховод проходит сквозь стены и перекрытия,
 * не оставляя дыр, и при этом выглядит сплошным. В вентиляционной системе
 * считается обычным участком магистрали (по префиксу vent_ в id блока).
 */
public class SolidVentBlock extends Block {
    public SolidVentBlock(Properties props) {
        super(props);
    }
}
