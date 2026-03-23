ServerEvents.recipes(event => {
  // Tier 1 Kütüphanesi
  event.shaped('enchantmentlibrary:library_tier1', [
    'S E S',
    'C B C',
    'S R S'
  ], {
    S: 'minecraft:stone_bricks',
    E: 'minecraft:emerald_block',
    C: 'minecraft:end_crystal',
    B: 'minecraft:bookshelf',
    R: 'minecraft:ender_chest'
  }).id('kubejs:enchantmentlibrary/library_tier1')

  // Tier 2 Kütüphanesi
  event.shaped('enchantmentlibrary:library_tier2', [
    ' D ',
    'X L X',
    ' T '
  ], {
    D: 'minecraft:diamond_block',
    X: 'minecraft:experience_bottle',
    L: 'enchantmentlibrary:library_tier1',
    T: 'minecraft:nether_star'
  }).id('kubejs:enchantmentlibrary/library_tier2')

  // Tier 3 Kütüphanesi
  event.shaped('enchantmentlibrary:library_tier3', [
    ' N ',
    'O B O',
    ' H '
  ], {
    N: 'minecraft:netherite_block',
    O: 'minecraft:echo_shard',
    B: 'enchantmentlibrary:library_tier2',
    H: 'minecraft:heavy_core'
  }).id('kubejs:enchantmentlibrary/library_tier3')
})
