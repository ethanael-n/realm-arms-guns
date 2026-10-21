# Third-party notices

## Musket Mod Reloaded (MIT)

Parts of the gun code are ported from Musket Mod Reloaded (mod id `musketmod`, by Lufurrius), which is
released under the MIT License:

- `src/main/java/net/gunmetal/arms/guns/GunItem.java` (loading/firing flow)
- `src/main/java/net/gunmetal/arms/entity/GunBullet.java` (bullet flight, hits and damage)
- `src/main/java/net/gunmetal/arms/entity/DeferredDamage.java`
- `src/main/java/net/gunmetal/arms/ai/RangedGunAttackGoal.java`
- `src/main/java/net/gunmetal/arms/client/GunClient.java` (from its ClientUtilities)

MIT License

Copyright (c) 2026 Ethanael

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## Sounds

See `src/main/resources/assets/gunmetal/sounds/credits.txt`: sounds carried over from Musket Mod
Reloaded, built from CC0 recordings on freesound.org.

## Bundled library

Ranged Weapon API is pulled from Modrinth's maven at build time and bundled into the built jar
(Jar-in-Jar); it is not part of this repository's source.
