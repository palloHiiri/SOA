import argparse
from concurrent.futures import ThreadPoolExecutor
import http.cookiejar
import json
import os
import ssl
import threading
import time
import urllib.error
import urllib.request
import uuid


def main():
    parser = argparse.ArgumentParser(description='API regression and concurrent-write checks against a running local stack')
    parser.add_argument('--base', default='https://localhost:8443/api/v1/')
    parser.add_argument('--login', default='sys')
    parser.add_argument('--insecure', action='store_true', help='Accept local self-signed TLS certificate')
    args = parser.parse_args()
    password = os.environ['TICKETS_TEST_PASSWORD']
    context = ssl._create_unverified_context() if args.insecure else ssl.create_default_context()
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar),
                                        urllib.request.HTTPSHandler(context=context))
    req = urllib.request.Request(args.base + 'sso-ident/auth/login',
                                 json.dumps({'login': args.login, 'password': password}).encode(),
                                 {'Content-Type': 'application/json'})
    with opener.open(req, timeout=30) as response:
        response.read()
    cookie = '; '.join(c.name + '=' + c.value for c in jar)

    def api(path, data=None, method=None, expected=200):
        req = urllib.request.Request(args.base + 'tickets/' + path,
                                     None if data is None else json.dumps(data).encode(),
                                     {'Content-Type': 'application/json', 'Cookie': cookie}, method=method)
        try:
            response = urllib.request.urlopen(req, context=context, timeout=30)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            status = response.code
            raw = response.read()
        body = json.loads(raw) if raw else None
        if expected is not None:
            assert status == expected, (path, status, body)
        return status, body

    trains = api('train-sets?size=100')[1]['content']
    assert trains, 'At least one CDC train-set snapshot is required'
    train_id = trains[0]['id']
    carriage = api(f'train-sets/{train_id}/carriages?size=100')[1]['content'][0]
    carriage_number = carriage['carriageNumber']
    seat = api(f'train-sets/{train_id}/carriages/{carriage_number}/seats?size=100')[1]['content'][0]
    venue = api('venues', {'name': '__jpa_test_' + uuid.uuid4().hex, 'trainSetId': train_id}, expected=201)[1]
    venue_id = venue['id']
    payload = {'venueId': venue_id, 'carriageNumber': carriage_number, 'seatNumber': seat['seatNumber'],
               'basePrice': 123.45, 'discount': 10, 'refundable': False, 'type': 'USUAL'}
    try:

        api('tickets', dict(payload, basePrice=10000000000), expected=400)
        assert api('search', {'venueId': venue_id})[1]['totalElements'] == 0
        print('Atomic ticket + initial price rollback: OK')

        barrier = threading.Barrier(4)

        def create_concurrently(_):
            barrier.wait(timeout=10)
            return api('tickets', payload, expected=None)

        with ThreadPoolExecutor(max_workers=4) as pool:
            results = list(pool.map(create_concurrently, range(4)))
        assert sorted(status for status, _ in results) == [201, 409, 409, 409], results
        ticket = next(body for status, body in results if status == 201)
        ticket_id = ticket['id']
        assert ticket['creationDate'] and ticket['basePrice'] == 123.45
        assert api('search', {'venueId': venue_id})[1]['totalElements'] == 1
        print('Concurrent same-seat creation: one success, three conflicts, no duplicates: OK')

        status, candidate = api(f'tickets/{ticket_id}/available-seat', expected=None)
        assert status in (200, 204), (status, candidate)
        if status == 200:
            assert candidate['seatNumber'] != ticket['seatNumber']
            second = api('tickets', dict(payload, seatNumber=candidate['seatNumber']), expected=201)[1]
            next_status, next_seat = api(f'tickets/{ticket_id}/available-seat', expected=None)
            assert next_status in (200, 204), (next_status, next_seat)
            if next_status == 200:
                assert next_seat['seatNumber'] not in (ticket['seatNumber'], second['seatNumber'])
            api('tickets/' + str(second['id']), method='DELETE', expected=204)
        print('Available-seat query excludes source and existing tickets: OK')

        for field in ['id', 'name', 'creationDate', 'carriageNumber', 'seatNumber', 'basePrice', 'discount', 'refundable', 'type']:
            result = api('search', {field: ticket[field], 'venueId': venue_id, 'sort': [field + ',desc']})[1]
            assert result['totalElements'] == 1 and result['content'][0]['id'] == ticket_id, field
        api(f'tickets/{ticket_id}', dict(payload, name='JPA updated ticket'), method='PUT')
        assert api(f'tickets/{ticket_id}')[1]['name'] == 'JPA updated ticket'
        price = api('price-histories', {'ticketId': ticket_id, 'basePrice': 99.99, 'discount': 25}, expected=201)[1]
        assert price['changedDate'] and api('price-histories/' + str(price['id']))[1]['discount'] == 25
        assert api('price-histories?ticketId=' + str(ticket_id))[1]['totalElements'] == 2
        assert api(f'tickets/{ticket_id}')[1]['basePrice'] == 99.99
        assert api('tickets/by-discount-below/30?venueId=' + str(venue_id))[1]['totalElements'] == 1

        barrier = threading.Barrier(8)

        def update_venue(index):
            barrier.wait(timeout=10)
            name = venue['name'] + '_' + str(index)
            return api('venues/' + str(venue_id), {'name': name, 'trainSetId': train_id},
                       method='PUT', expected=None)

        with ThreadPoolExecutor(max_workers=8) as pool:
            updates = list(pool.map(update_venue, range(8)))
        assert any(status == 200 for status, _ in updates), updates
        assert all(status in (200, 409) for status, _ in updates), updates
        successful_names = {body['name'] for status, body in updates if status == 200}
        assert api('venues/' + str(venue_id))[1]['name'] in successful_names
        print('Concurrent row updates: committed result or HTTP 409, no server errors: OK')

        def append_prices():
            for discount in range(30, 40):
                for attempt in range(5):
                    status, body = api('price-histories', {'ticketId': ticket_id, 'basePrice': discount,
                                                         'discount': discount}, expected=None)
                    if status == 201:
                        break
                    assert status == 409, body
                else:
                    raise AssertionError('Price append repeatedly conflicted')

        def read_pages():
            for _ in range(40):
                page = api('price-histories?size=100&ticketId=' + str(ticket_id))[1]
                assert page['totalElements'] == len(page['content']), page
                current = api(f'tickets/{ticket_id}')[1]
                assert (current['basePrice'], current['discount']) == (99.99, 25) or current['basePrice'] == current['discount']

        with ThreadPoolExecutor(max_workers=2) as pool:
            futures = [pool.submit(append_prices), pool.submit(read_pages)]
            for future in futures:
                future.result()
        print('Count/page snapshot and coherent latest-price reads during concurrent writes: OK')
        api('tickets/latest')
        api('tickets/discount/average')
        api('venues/' + str(venue_id), method='DELETE', expected=409)
        print('Filters, sorts, ticket update, latest price, history, specials and venue deletion guard: OK')
    finally:

        tickets = api('search', {'venueId': venue_id, 'size': 100})[1]['content']
        for ticket in tickets:
            for attempt in range(5):
                status, _ = api('tickets/' + str(ticket['id']), method='DELETE', expected=None)
                if status in (204, 404):
                    break
                assert status == 409
                time.sleep(0.1)
            else:
                raise AssertionError('Could not clean up test ticket')
        api('venues/' + str(venue_id), method='DELETE', expected=204)
        print('Test data cleanup: OK')


if __name__ == '__main__':
    main()
