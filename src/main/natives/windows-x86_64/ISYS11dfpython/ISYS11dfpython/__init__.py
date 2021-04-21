import sys

ucs4 = sys.maxunicode > 65535
version = sys.version_info[0:2]

if version == (3, 3):
	from . libISYS11dfpython3_3 import *
elif version == (2, 7):
	if ucs4:
		from . libISYS11dfpython2_7u4 import *
	else:
		from . libISYS11dfpython2_7 import *
elif version == (2, 6):
	if ucs4:
		from . libISYS11dfpython2_6u4 import *
	else:
		from . libISYS11dfpython2_6 import *
else:
	raise Exception("Document Filters requires Python version 2.6, 2.7, or 3.3.")

