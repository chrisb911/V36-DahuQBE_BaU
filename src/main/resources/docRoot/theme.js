const theme = {
    overrides: {
        MuiSvgIcon: {
            root: {
                verticalAlign:'middle',
            },
        }
    },
    typography: {
        useNextVariants: true,
        fontSize: 12,
    },
    palette: {
        primary: {
            main:'#0099e3',
        },
        secondary: {
            main:'#868888',
        },

        error: {
            main:'#ff0000',
        },
        // Used by `getContrastText()` to maximize the contrast between the background and
        // the text.
        contrastThreshold: 3,
        // Used to shift a color's luminance by approximately
        // two indexes within its tonal palette.
        // E.g., shift from Red 500 to Red 300 or Red 700.
        tonalOffset: 0.2,
    },
    def:{
        dahuLogo: "images/themeImages/dahu-small-green.png",
	    brandLogo: "images/themeImages/dahu-small-qbe-white.png",
	    largeBrandLogo:"images/themeImages/dahu-large-qbe.png",
        fullScreen: "images/themeImages/full-screen-qbe.jpg",
        horizScreen: "images/themeImages/full-screen-qbe-light.png",
        vertScreen: "images/themeImages/full-screen-qbe-light.jpg",
    }
};
