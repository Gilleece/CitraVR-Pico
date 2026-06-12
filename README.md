<h1 align="center">
  <br>
  <a href="https://citra-emu.org/"><img src="assets/citra-vr-pico.jpg" alt="CitraVR-Pico" width="500"></a> (Beta)
</h1>

<h4 align="center"> Play 3DS homebrew and personal game backups in 3D on the go with your Pico headset.
</br>
  CitraVR is a GPL-licensed, engineless OpenXR application with all source code publicly available.
</h4>

> [!NOTE]
> **CitraVR-Pico is a fork of [amwatson/CitraVR](https://github.com/amwatson/CitraVR) that ports the project to PICO headsets.**
> All original work and credit belongs to the upstream CitraVR project. This fork focuses on adapting CitraVR to run on PICO devices; everything else is built on top of the excellent foundation laid by the original authors.

<p align="center">
  <a href="#compatabillty">Compatibillty</a> |
  <a href="#releases">Releases</a> |
  <a href="#known-issues">Known Issues</a> |
  <a href="#how-to-install-and-run">How to Install and Run</a> |
  <a href="#building">Building</a> |
  <a href="#discord">Discord</a> |
  <a href="#need-help">Need Help?</a> |
  <a href="#support">Support</a> |
  <a href="#credits">Credits</a> |
  <a href="#license">License</a>
</p>

## Introduction

CitraVR was originally built for Meta Quest headsets. **This fork ([CitraVR-Pico](https://github.com/Gilleece/CitraVR-Pico)) ports CitraVR to PICO headsets**, adapting input, rendering, and immersive-mode handling for PICO's OpenXR runtime while keeping the rest of the original project intact.

## Features
- Stereoscopic rendering
- Broad controller support
- Large, moveable/resizeable screen
- Playable in mixed reality
- Low-overhead port
- Fully GPL-licensed, 100% independent of the Meta SDK

## Compatibillty

### HMDs
CitraVR-Pico targets PICO headsets, including:
- PICO 4
- PICO 4 Ultra

> Looking for the Meta Quest version? See the original [CitraVR](https://github.com/amwatson/CitraVR), which supports Meta Quest 2, Quest Pro, Quest 3, and Quest 3S.

### Games
For a full list of games that work well on CitraVR, please visit the [CitraVR Game Compatability List](https://docs.google.com/spreadsheets/d/1viN8_MWO1HW9QXlkT-TdCGQbH1g660mKsIp1ZTARdho/edit?usp=sharing)

### Controllers/Input 
[Touch Controller Input Bindings Diagram](https://github.com/amwatson/CitraVR/wiki/Touch-Controller-Input-Bindings)

For games that need access to more inputs, or if a player needs to access more inputs faster, CitraVR also supports a multitude of 3rd party wired USB and wireless bluetooth controllers. 

## Releases
Grab the latest CitraVR-Pico release [here](https://github.com/Gilleece/CitraVR-Pico/releases).

Releases for the original Meta Quest build are available on the [upstream CitraVR releases page](https://github.com/amwatson/CitraVR/releases).

## Known Issues
See the [CitraVR Known Issues](https://github.com/amwatson/CitraVR/wiki/CitraVR-Known-Issues)

## How to Install and Run
Install the CitraVR-Pico APK by sideloading it onto your PICO headset (e.g. via SideQuest or `adb install`). The original wiki guides below were written for Quest but remain a useful reference for general setup and game backups:
- [How to install and run CitraVR (Quest guide)](https://github.com/amwatson/CitraVR/wiki/Install-Run-on-Quest)
- [How to back up 3DS Games](https://github.com/amwatson/CitraVR/wiki/Backing-up-3DS-Games)

## Building
The build process largely follows the upstream instructions, retargeted at PICO's OpenXR runtime:
- [Building for Quest (upstream reference)](https://github.com/amwatson/CitraVR/wiki/Building-for-Quest)

## Discord 
Join the [Flat2VR](https://flat2vr.com/) discord and from there join [cvr-join](https://discord.com/channels/747967102895390741/1196505250102792232) to get access to the CitraVR community and support forums

# Need Help?
Please check our [Troubleshooting](https://github.com/amwatson/CitraVR/wiki/Troubleshooting) and [Known Issues](https://github.com/amwatson/CitraVR/wiki/CitraVR-Known-Issues) pages to see if your issue is listed.
To file a bug report or a feature request, please [submit an issue](https://github.com/amwatson/CitraVR/issues/new/choose).
Otherwise, follow the instructions for <a href="#discord">Discord</a> and post in [cvr-support](https://discord.com/channels/747967102895390741/1196505719910957176)

## Support
[Buy the original creator, Amanda Watson, a beer](https://www.buymeacoffee.com/fewerwrong)

## Credits
CitraVR-Pico is a fork of [CitraVR](https://github.com/amwatson/CitraVR) by [amwatson](https://github.com/amwatson) and contributors, which is itself a VR port of the [Citra 3DS emulator](https://github.com/citra-emu/citra). Huge thanks to the original author — this fork would not exist without her work. The PICO port is maintained by [Gilleece](https://github.com/Gilleece).

## License
CitraVR-Pico, like CitraVR, is licensed under the GPLv3 (or any later version). Refer to the [LICENSE.txt](https://github.com/amwatson/CitraVR/blob/master/license.txt) file.
