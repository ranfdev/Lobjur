# Translating
## Using POEdit
To create a new translation of Lobjur you can use POEdit:
- Install POEdit from a Flatpak: `flatpak install flathub net.poedit.Poedit`; otherwise use your distribution's repositories;
- Open POEdit and select `Create new translation from POT file`;
- In the file selection dialog, select the `com.ranfdev.Lobjur.pot` file in the repository's `po` directory;
- Once you have finished translating, open a pull request from your forked repository to the source repository on the `develop` branch.

---

## Updating the source
Whenever you add or update a translation, update the source translation from source code changes with this command in the project's root directory:

`xgettext --from-code=UTF-8  --add-comments --keyword=_ --keyword=C_:1c,2 --output=po/com.ranfdev.Lobjur.pot -f po/POTFILES`

Thank you for translating.