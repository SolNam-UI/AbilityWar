package daybreak.abilitywar.ability.list;

import daybreak.abilitywar.ability.AbilityBase;
import daybreak.abilitywar.ability.AbilityManifest;
import daybreak.abilitywar.ability.AbilityManifest.Rank;
import daybreak.abilitywar.ability.AbilityManifest.Species;
import daybreak.abilitywar.ability.SubscribeEvent;
import daybreak.abilitywar.ability.decorator.ActiveHandler;
import daybreak.abilitywar.config.ability.AbilitySettings.SettingObject;
import daybreak.abilitywar.game.AbstractGame.Participant;
import daybreak.abilitywar.utils.base.Formatter;
import daybreak.abilitywar.utils.base.concurrent.TimeUnit;
import daybreak.abilitywar.utils.base.math.LocationUtil;
import daybreak.abilitywar.utils.base.math.LocationUtil.Predicates;
import daybreak.abilitywar.utils.base.math.geometry.Circle;
import daybreak.abilitywar.utils.base.minecraft.version.ServerVersion;
import daybreak.abilitywar.utils.library.ParticleLib;
import daybreak.abilitywar.utils.library.ParticleLib.RGB;
import daybreak.abilitywar.utils.library.item.ItemLib;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.inventory.EntityEquipment;

import java.util.function.Predicate;

@AbilityManifest(name = "컬스", rank = Rank.A, species = Species.OTHERS, explain = {
		"주위 15칸 안에 있는 상대를 원거리에서 타겟팅해 $[DurationConfig]초간 지속되는",
		"저주 인형을 내 위치에 만들어내며, 저주 인형이 대미지를 입을 경우",
		"대미지의 일부가 상대에게 전이됩니다. $[CooldownConfig]",
		"대상의 체력이 적을 수록 더욱 큰 대미지를 입힐 수 있습니다."
})
public class Curse extends AbilityBase implements ActiveHandler {

	public static final SettingObject<Integer> CooldownConfig = new SettingObject<Integer>(Curse.class, "Cooldown", 100,
			"# 쿨타임") {

		@Override
		public boolean Condition(Integer value) {
			return value >= 0;
		}

		@Override
		public String toString() {
			return Formatter.formatCooldown(getValue());
		}

	};

	public static final SettingObject<Integer> DurationConfig = new SettingObject<Integer>(Curse.class, "Duration", 10,
			"# 지속시간") {

		@Override
		public boolean Condition(Integer value) {
			return value >= 0;
		}

	};

	public Curse(Participant participant) {
		super(participant);
	}

	private final Predicate<Entity> STRICT = Predicates.STRICT(getPlayer());

	@Override
	public boolean ActiveSkill(Material materialType, ClickType clickType) {
		if (materialType.equals(Material.IRON_INGOT) && !skill.isDuration() && !cooldownTimer.isCooldown()) {
			Player player = LocationUtil.getEntityLookingAt(Player.class, getPlayer(), 15, STRICT);
			if (player != null) {
				target = player;
				skill.start();
			}
		}
		return false;
	}

	private static final RGB BLACK = RGB.of(1, 1, 1);

	private Player target = null;
	private ArmorStand armorStand = null;

	private final CooldownTimer cooldownTimer = new CooldownTimer(CooldownConfig.getValue());
	private final DurationTimer skill = new DurationTimer(DurationConfig.getValue() * 10, cooldownTimer) {
		private int particle;

		@Override
		protected void onDurationStart() {
			armorStand = target.getWorld().spawn(getPlayer().getLocation(), ArmorStand.class);
			if (ServerVersion.getVersionNumber() >= 10) armorStand.setInvulnerable(true);
			armorStand.setCustomName(target.getName());
			armorStand.setCustomNameVisible(true);
			armorStand.setBasePlate(false);
			armorStand.setArms(true);
			armorStand.setGravity(false);
			EntityEquipment equipment = armorStand.getEquipment();
			equipment.setArmorContents(target.getInventory().getArmorContents());
			equipment.setHelmet(ItemLib.getSkull(target.getName()));
			this.particle = 0;
		}

		@Override
		protected void onDurationProcess(int seconds) {
			if (++particle >= 10) {
				showHelix(armorStand.getLocation());
				particle = 0;
			}
			Location location = armorStand.getLocation();
			location.setYaw(location.getYaw() + 5);
			armorStand.teleport(location);
		}

		@Override
		protected void onDurationEnd() {
			target = null;
			armorStand.remove();
			armorStand = null;
		}

		@Override
		protected void onDurationSilentEnd() {
			target = null;
			armorStand.remove();
			armorStand = null;
		}
	}.setPeriod(TimeUnit.TICKS, 2);

	private static final int particleCount = 20;
	private static final double yDiff = 0.6 / particleCount;
	private static final Circle helixCircle = Circle.of(0.5, particleCount);

	private void showHelix(Location target) {
		new Timer((particleCount * 3) / 2) {
			int count = 0;

			@Override
			protected void run(int a) {
				for (int i = 0; i < 2; i++) {
					ParticleLib.REDSTONE.spawnParticle(target.clone().add(helixCircle.get(count % 20)).add(0, count * yDiff, 0), Curse.BLACK);
					count++;
				}
			}
		}.setPeriod(TimeUnit.TICKS, 1).start();
	}

	@SubscribeEvent
	private void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
		onEntityDamage(e);
	}

	@SubscribeEvent
	private void onEntityDamageByBlock(EntityDamageByBlockEvent e) {
		onEntityDamage(e);
	}

	@SubscribeEvent
	private void onEntityDamage(EntityDamageEvent e) {
		if (skill.isRunning() && e.getEntity().equals(armorStand)) {
			e.setCancelled(true);
			target.damage(e.getDamage() * (6 * (1 / Math.max(target.getHealth(), 0.01))), armorStand);
		}
	}

	@SubscribeEvent
	private void onPlayerArmorStandManipulate(PlayerArmorStandManipulateEvent e) {
		if (e.getRightClicked().equals(armorStand)) e.setCancelled(true);
	}

}
