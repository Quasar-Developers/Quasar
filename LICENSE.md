# Quasar Licensing Info

Quasar is licensed under two main licenses, the GNU Affero General Public License 3.0
(or any later version) and the Creative Commons Attribution-ShareAlike 4.0 International
license. Quasar is based off of Horizon End's Ion plugin, which at the time of writing,
is available under the GNU General Public License 3.0. A copy of each license included in
Quasar can be found in the `LICENSES` directory at the project root. Further licensing
information for different parts of the codebase is available below.

## Code Licensing (`*.java`/`*.kt`/all build files/Quasar datapack)

All of Quasar's code is licensed under the GNU AGPL 3.0 license (or any later version),
with a linking exception for Minecraft that can be found in the `GPL-3.0-linking-exception.txt`
file. By contributing code to Quasar, you agree to permanently license your code to Quasar and
all of its players under the AGPL.

The AGPL dictates that any server running Quasar must make all of its code and any
modifications made to its code available to all of its players. This also applies
to any plugins that the server is running; running Quasar with plugins that are not
compatible with the AGPL is a violation of these terms. This does not include the code
for Minecraft, which is excluded from the license under the aforementioned linking exception.

The easiest way to comply with this is to host your changes to Quasar on your forge of
choice (codeberg/github/gitlab/etc) and to provide players with links to your repository, 
links to the repositories for any plugins that you use, and a link to the versions of paper
and Minecraft that your server is running. Providing version info for all code your server
is running can help players find the right version of the source code.

## Asset Licensing (all resource files)

Most of Quasar's resources are licensed under variations of the Creative Commons 4.0
International license, however not all assets follow this convention. The most common
license is the Creative Commons Attribution-ShareAlike 4.0 International license,
which requires that anybody who uses the asset provides attribution to its creator and
that any assets derived from it be released under the same license.

Some assets, for one reason or another may be licensed under a non-free license such
as one of the Creative Commons non-commercial licenses. Generally, we do not accept
non-free assets except for a very good reason, and we will always welcome suggestions
for free assets to replace them. All non-free assets are listed in the `NONFREE.md` file
at the project root.

In order to simplify code licensing, all assets have an associated attributions.json file
with information about the creator, where it was found, and what license it is being used
under. These files are used to automatically generate attribution info and to ensure license
compliance.
