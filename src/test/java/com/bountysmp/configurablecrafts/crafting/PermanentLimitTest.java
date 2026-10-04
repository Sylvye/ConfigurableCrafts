package com.bountysmp.configurablecrafts.crafting;

import com.bountysmp.configurablecrafts.BukkitTest;
import com.bountysmp.configurablecrafts.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class PermanentLimitTest extends BukkitTest {
    @TempDir File folder;
    @Test void permanentUsageSurvivesRestartAndRecipeRecreation(){
        var now=new AtomicLong(1000);var file=new File(folder,"usage.yml");var recipe=new ManagedRecipe("mythic_guardian",RecipeKind.SHAPED);recipe.globalLimit().set(1,0);
        var first=new CraftLimitTracker(null,file,now::get);assertNull(first.tryConsume(recipe,UUID.randomUUID(),1));now.set(1000000000000L);
        var second=new CraftLimitTracker(null,file,now::get);second.load();var recreated=new ManagedRecipe(recipe.id(),RecipeKind.SHAPED);recreated.globalLimit().set(1,0);assertNotNull(second.check(recreated,UUID.randomUUID(),1));
        recreated.globalLimit().set(1,60);assertNotNull(second.check(recreated,UUID.randomUUID(),1));
    }
    @Test void validationDoesNotUseAllowance(){var recipe=new ManagedRecipe("mythic_guardian",RecipeKind.SHAPED);recipe.globalLimit().set(1,0);var tracker=new CraftLimitTracker(null,new File(folder,"usage.yml"));for(int n=0;n<10;n++)assertNull(tracker.check(recipe,UUID.randomUUID(),1));assertNull(tracker.tryConsume(recipe,UUID.randomUUID(),1));assertNotNull(tracker.tryConsume(recipe,UUID.randomUUID(),1));}
}
