# `attribution.json` file format

Attribution files are used to indicate the source of assets used in Quasar and
to ensure that all asset creators are correctly credited for their work, in
compliance with each asset's individual license, as they often cannot be specified
within the asset due to file format limitations.

Each attribution.json file is an array of objects describing each file within the
directory that the attribution file is contained in, for example, a directory with
the following structure would have two attribution files, one with three entries for
the top level directory, and one with one entry for the subdirectory.
```
./
L attribution.json
L generic_texture_1.png
L generic_texture_2.png
L generic_texture_3.png
L machine1/
  L attribution.json
  L machine_texture_1.png
```

Each object in the top-level array follows the format shown below (a schema and
workflow that can be used for validation is planned for the future):
```typescript
{
    // The name of the file being attributed.
    "file": string,

    // The license of the asset. Preferrably a SPDX license identifier
    "license": string,

    // All authors or editors of the asset
    "authors": {    
        // The author's name or alias
        "name": string,

        // An optional comment explaining the author's relation to the asset
        "comment"?: string,

        // An optional link to the author's profile or home page
        "link"?: string
    }[],

    // All sources used to create the asset
    "sources": {
        // A link to the source
        "link": string,

        // The license the source is licensed under. Preferrably a SPDX license identifier.
        "license": string,

        // An optional comment explaining the source's relation to the asset
        "comment"?: string
    }[]
}
```

An example attribution file is shown below:
```jsonc
[
    {
        "file": "klaxon2.ogg",
        "license": "CC-BY-NC-4.0",
        "authors": [
            { "name": "ryansnook", "comment": "Creator", "link": "https://freesound.org/people/ryansnook/" },
            { "name": "XWASHERE", "comment": "Converted to ogg", "link": "https://xutils.org" },
            { "name": "John Reactor", "comment": "Fixed volume clipping" }
        ],
        "sources": [
            { "link": "https://freesound.org/people/ryansnook/sounds/103580/", "license": "CC-BY-NC-4.0", "comment": "Original sound" }
        ]
    }
]
```
