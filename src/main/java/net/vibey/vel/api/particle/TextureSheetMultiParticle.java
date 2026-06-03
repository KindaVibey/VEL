package net.vibey.vel.api.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class TextureSheetMultiParticle extends SingleQuadMultiParticle {

    protected TextureAtlasSprite sprite;

    protected TextureSheetMultiParticle(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    protected TextureSheetMultiParticle(
            ClientLevel level, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
    }

    protected void setSprite(TextureAtlasSprite sprite) {
        this.sprite = sprite;
    }

    public void pickSprite(SpriteSet sprite) {
        this.setSprite(sprite.get(this.random));
    }

    public void setSpriteFromAge(SpriteSet sprite) {
        if (!this.removed) {
            this.setSprite(sprite.get(this.age, this.lifetime));
        }
    }

    @Override
    protected float getU0() {
        return this.sprite.getU0();
    }

    @Override
    protected float getU1() {
        return this.sprite.getU1();
    }

    @Override
    protected float getV0() {
        return this.sprite.getV0();
    }

    @Override
    protected float getV1() {
        return this.sprite.getV1();
    }
}