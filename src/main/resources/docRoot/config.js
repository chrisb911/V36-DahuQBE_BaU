const configs = {
    debug : "false",
    appname : "QBE Search",
    signin : "DEF",
    suggest: "queries",
    PDFViewer:true,
    MarkupViewer:true,
    features: {
        "SavedConstraints":"true",
        "HomePageHeaderSearchOptions":false,
        "HomePageHeader":true,
        "HomePageFilters":true,
        "HomePageHeaderSavedSearches":true,
        "ResultsPageSearchOptions":true,
        "ResultsPageHeaderSavedSearches":true,
        "HomePageSavedSearches":false,
        "ResultShowParentLink":true,
        "ResultShowSourceIcon":true,
        "ResultsHeaderSearchWithin":true,
        "ResultsPageHeaderSearch":true,
        "ShowSearchHistoryOption": true,             // show the option 'show history' in header menu (also depends if Surface has useHistory enabled)
        "ShowEmailResultsOption": true,               // show the option 'email results' in header menu. Uses client's installed email app.
        "enableSearchSuggestions":true,               // if false,the search entry boxes won't make query suggestions, regardless of the settings in Surface
        "ResultsPageResultOptions":true,               // not fully implemented - actions for each result, including add to export basket, flag, comment etc
        "ResultsPageResultOptionsSets":false,
        "ResultsPageResultOptionsFlags":true,
        "ResultsPageResultOptionsComments":true
    }
    ,
    google :{
        googleclientid : "360146944373-fc0ueql4fm9kqfu75g5re86h3sdp68tk.apps.googleusercontent.com",
        endUserEmulation:false
    }

};
