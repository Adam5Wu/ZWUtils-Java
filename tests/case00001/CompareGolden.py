import sys
import difflib

if 'HOST_RESERVED_EXCEPTIONS' not in locals():
    HOST_RESERVED_EXCEPTIONS = []

if 'HOST_LOG' not in locals():
	HOST_LOG = lambda data: sys.stdout.write(data+'\n')

Test_File = 'test.log'
Golden_File = 'Golden.log'

def DoCompare():
	HOST_LOG("Loading test case generated data '%s'..."%Test_File)
	TestLog = [item.strip() for item in open(Test_File, 'r').readlines()]
	HOST_LOG("Loading known good data '%s'..."%Golden_File)
	GoldenLog = [item.strip() for item in open(Golden_File, 'r').readlines()]

	LogDiff = difflib.unified_diff(TestLog, GoldenLog)
	DiffCnt = 0
	for DiffLine in LogDiff:
		DiffCnt += 1
		HOST_LOG(DiffLine)
	
	HOST_LOG("Generated %d lines of differences"%DiffCnt)
	return DiffCnt == 0

if __name__ == "__main__":
	try:
		RESULT = DoCompare()
	except HOST_RESERVED_EXCEPTIONS:
		raise
	except Exception,e:
		HOST_EXCEPT("Failed to compare result - %s"%e)
		RESULT = False

