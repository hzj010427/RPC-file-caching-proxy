#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>



int main(int argc, char *argv[]) {
    // slowly(sleeping after each reading) a file
    const char *filename = argv[1];
    long offset = atol(argv[2]);
    int fd = open(filename, O_RDWR);

    if (fd == -1) {
        perror("open");
        exit(1);
    }

    if (lseek(fd, offset, SEEK_SET) == -1) {
        perror("lseek");
        exit(1);
    }

    char buffer[10240];
    for (int i = 0; i < 10240; i++) {
        buffer[i] = '0';
    }

    long bytes_to_write = 1024 * 1024;
    long bytes_written = 0;
    // write 1MB of 0
    while (bytes_written < bytes_to_write) {
        ssize_t bytes_written_now = write(fd, buffer, sizeof(buffer));
        if (bytes_written_now == -1) {
            printf("write eof\n");
            break;
        }
        bytes_written += bytes_written_now;
        // sleep in miliseconds
        usleep(100000);
    }

    printf("closing file\n");

    close(fd);

    return 0;
}