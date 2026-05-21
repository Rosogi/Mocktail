def main(key = None):
    millis = runtime.now_epoch_millis()
    suffix = runtime.random_alnum(16)
    return str(millis) + suffix
